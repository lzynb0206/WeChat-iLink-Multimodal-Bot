package com.example.demo.service.wechat;

import com.example.demo.model.ActionType;
import com.example.demo.model.MessageRouteResult;
import com.example.demo.model.ReplyMode;
import com.example.demo.service.ai.AlibabaAiService;
import com.example.demo.service.audio.AudioTranscoder;
import com.example.demo.service.routing.MessageRouter;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class WechatBotService implements DisposableBean {
    private final AlibabaAiService aiService;
    private final AudioTranscoder audioTranscoder;
    private final MessageRouter messageRouter;
    private ILinkClient client;
    private ExecutorService botExecutor;

    @Value("${wechat.bot.enabled:false}")
    private boolean enabled;

    @Value("${wechat.bot.download-dir:downloads}")
    private String downloadDir;

    @Value("${wechat.bot.qr-code-path:wechat-login-qr.png}")
    private String qrCodePath;

    public WechatBotService(
            AlibabaAiService aiService,
            AudioTranscoder audioTranscoder,
            MessageRouter messageRouter) {
        this.aiService = aiService;
        this.audioTranscoder = audioTranscoder;
        this.messageRouter = messageRouter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBot() {
        if (!enabled) {
            log.info("微信机器人未启用；设置 WECHAT_BOT_ENABLED=true 后启动登录");
            return;
        }
        botExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "wechat-ilink-login");
            thread.setDaemon(true);
            return thread;
        });
        botExecutor.submit(this::startBot);
    }

    private void startBot() {
        client = ILinkClient.builder()
                .onLogin(new OnLoginListener() {
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        log.info("微信机器人登录成功 botId={}", context.getBotId());
                    }

                    @Override
                    public void onLoginFailure(Throwable throwable) {
                        log.error("微信机器人登录失败", throwable);
                    }
                })
                .onMessage(new OnMessageListener() {
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        for (WeixinMessage message : messages) {
                            processMessage(message);
                        }
                    }
                })
                .build();

        try {
            String qrCodeContent = client.executeLogin();
            Path qrPath = writeQrCode(qrCodeContent);
            log.info("微信登录二维码已生成：{}，请用微信扫码", qrPath);
            LoginContext context = client.getLoginFuture().get();
            log.info("微信登录完成 botId={}", context.getBotId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("微信机器人登录线程被中断");
        } catch (Exception exception) {
            log.error("微信机器人启动失败", exception);
        }
    }

    private void processMessage(WeixinMessage message) {
        String fromUserId = message.getFrom_user_id();
        List<MessageItem> items = message.getItem_list();
        if (items == null) {
            return;
        }
        for (MessageItem item : items) {
            if (item.getText_item() != null) {
                handleText(fromUserId, item.getText_item().getText());
            } else if (item.getImage_item() != null) {
                handleImage(fromUserId, item);
            } else if (item.getVoice_item() != null) {
                handleVoice(fromUserId, item);
            }
        }
    }

    private Path writeQrCode(String content) throws Exception {
        Path target = Path.of(qrCodePath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BitMatrix matrix = new QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, 360, 360);
        MatrixToImageWriter.writeToPath(matrix, "PNG", target);
        return target;
    }

    private void handleText(String fromUserId, String userText) {
        log.info("收到微信文本消息 userId={}", fromUserId);
        try {
            client.startTyping(fromUserId);
            MessageRouteResult route = messageRouter.route(userText, ReplyMode.TEXT);
            executeRoute(fromUserId, route);
        } catch (Exception exception) {
            log.error("处理文本消息失败 userId={}", fromUserId, exception);
            sendErrorMessage(fromUserId, readableError(exception));
        } finally {
            stopTypingQuietly(fromUserId);
        }
    }

    private void handleVoice(String fromUserId, MessageItem item) {
        log.info("收到微信语音消息 userId={}", fromUserId);
        try {
            client.startTyping(fromUserId);
            byte[] silk = client.downloadVoiceFromMessageItem(item);
            byte[] wav = audioTranscoder.silkToWav(silk);
            String recognizedText = aiService.transcribeAudio(wav);
            log.info("微信语音识别完成 userId={}", fromUserId);
            MessageRouteResult route = messageRouter.route(recognizedText, ReplyMode.VOICE);
            executeRoute(fromUserId, route);
        } catch (Exception exception) {
            log.error("处理语音消息失败 userId={}", fromUserId, exception);
            sendErrorMessage(fromUserId, readableError(exception));
        } finally {
            stopTypingQuietly(fromUserId);
        }
    }

    private void executeRoute(String fromUserId, MessageRouteResult route) throws Exception {
        if (route.action() == ActionType.IMAGE_GENERATION) {
            byte[] image = aiService.generateImage(route.content());
            client.sendImage(fromUserId, image, "qwen-image.png", route.content());
            return;
        }
        sendReply(fromUserId, route.content(), route.replyMode());
    }

    private void sendReply(String fromUserId, String text, ReplyMode replyMode) throws Exception {
        if (replyMode == ReplyMode.TEXT) {
            client.sendText(fromUserId, text);
            return;
        }

        byte[] wav = aiService.synthesizeSpeech(text);
        client.sendFile(fromUserId, wav, "qwen-answer.wav", null);
    }

    private void handleImage(String fromUserId, MessageItem item) {
        try {
            Path directory = Path.of(downloadDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = Files.createTempFile(directory, "wechat-image-", ".jpg");
            byte[] imageBytes = client.downloadImageFromMessageItem(item);
            Files.write(target, imageBytes);
            String answer = aiService.understandImage(imageBytes, "image/jpeg");
            client.sendText(fromUserId, answer);
            log.info("微信图片已保存至 {}", target);
        } catch (Exception exception) {
            log.error("处理图片消息失败 userId={}", fromUserId, exception);
            sendErrorMessage(fromUserId, readableError(exception));
        }
    }

    private String readableError(Exception exception) {
        if (exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException) {
            return exception.getMessage();
        }
        return "消息处理失败，请检查网络和 API Key 后重试。";
    }

    private void stopTypingQuietly(String fromUserId) {
        try {
            client.stopTyping(fromUserId);
        } catch (Exception exception) {
            log.debug("停止输入状态失败 userId={}", fromUserId, exception);
        }
    }

    private void sendErrorMessage(String fromUserId, String message) {
        try {
            client.sendText(fromUserId, message);
        } catch (Exception exception) {
            log.debug("发送错误提示失败 userId={}", fromUserId, exception);
        }
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
        if (botExecutor != null) {
            botExecutor.shutdownNow();
        }
    }
}
