package com.example.demo;


import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class WechatBootService implements DisposableBean {

    private final LlmService llmService;
    private final MediaAiService mediaAiService;
    private ILinkClient client;
    private ExecutorService botExecutor;

    @Value("${wechat.bot.enabled:false}")
    private boolean enabled;

    @Value("${wechat.bot.download-dir:downloads}")
    private String downloadDir;

    @Value("${wechat.bot.qr-code-path:wechat-login-qr.png}")
    private String qrCodePath;

    public WechatBootService(LlmService llmService, MediaAiService mediaAiService) {
        this.llmService = llmService;
        this.mediaAiService = mediaAiService;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initBot() {
        if (!enabled) {
            log.info("微信机器人未启用；设置 WECHAT_BOT_ENABLED=true 后启动登录");
            return;
        }
        botExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "wechat-ilink-login");
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
                        for (WeixinMessage msg : messages) {
                            String fromUserId = msg.getFrom_user_id();
                            List<MessageItem> itemList = msg.getItem_list();
                            if (itemList == null) {
                                continue;
                            }

                            for (MessageItem item : itemList) {
                                // ====== 处理文本消息 ======
                                if (item.getText_item() != null) {
                                    String userText = item.getText_item().getText();
                                    handleText(fromUserId, userText);
                                }

                                // ====== 处理图片消息，下载媒体文件 ======
                                if (item.getImage_item() != null) {
                                    handleImage(fromUserId, item);
                                }

                                if (item.getVoice_item() != null) {
                                    handleVoice(fromUserId, item);
                                }
                            }
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("微信机器人登录线程被中断");
        } catch (Exception e) {
            log.error("微信机器人启动失败", e);
        }
    }

    private Path writeQrCode(String content) throws Exception {
        Path target = Path.of(qrCodePath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 360, 360);
        MatrixToImageWriter.writeToPath(matrix, "PNG", target);
        return target;
    }

    private void handleText(String fromUserId, String userText) {
        log.info("收到微信文本消息 userId={}", fromUserId);
        try {
            client.startTyping(fromUserId);
            boolean voiceReply = userText.startsWith("语音：") || userText.startsWith("语音:");
            String prompt = voiceReply ? userText.substring(3).trim() : userText;
            String aiAnswer = llmService.chat(prompt);
            client.sendText(fromUserId, aiAnswer);
            if (voiceReply && mediaAiService.isConfigured()) {
                byte[] wav = mediaAiService.synthesizeSpeech(aiAnswer);
                client.sendFile(fromUserId, wav, "deepseek-answer.wav", "DeepSeek 语音回复");
            }
        } catch (Exception e) {
            log.error("处理文本消息失败 userId={}", fromUserId, e);
        } finally {
            try {
                client.stopTyping(fromUserId);
            } catch (Exception e) {
                log.debug("停止输入状态失败 userId={}", fromUserId, e);
            }
        }
    }

    private void handleImage(String fromUserId, MessageItem item) {
        try {
            Path directory = Path.of(downloadDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = Files.createTempFile(directory, "wechat-image-", ".jpg");
            byte[] imageBytes = client.downloadImageFromMessageItem(item);
            Files.write(target, imageBytes);
            if (mediaAiService.isConfigured()) {
                String answer = mediaAiService.understandImage(imageBytes, "image/jpeg");
                client.sendText(fromUserId, answer);
            } else {
                client.sendText(fromUserId, "图片已收到。请配置 DASHSCOPE_API_KEY 后启用图片理解。");
            }
            log.info("微信图片已保存至 {}", target);
        } catch (Exception e) {
            log.error("处理图片消息失败 userId={}", fromUserId, e);
            try {
                client.sendText(fromUserId, "图片处理失败，请稍后重试。");
            } catch (Exception sendException) {
                log.debug("发送图片失败提示失败", sendException);
            }
        }
    }

    private void handleVoice(String fromUserId, MessageItem item) {
        try {
            Path directory = Path.of(downloadDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = Files.createTempFile(directory, "wechat-voice-", ".silk");
            Files.write(target, client.downloadVoiceFromMessageItem(item));
            client.sendText(fromUserId, "语音已收到并保存。当前微信语音为 SILK 格式，需要转换为 WAV 并提供公网地址后才能交给 paraformer-v2 识别。");
            log.info("微信 SILK 语音已保存至 {}", target);
        } catch (Exception e) {
            log.error("处理语音消息失败 userId={}", fromUserId, e);
            try {
                client.sendText(fromUserId, "语音处理失败，请稍后重试。");
            } catch (Exception sendException) {
                log.debug("发送语音失败提示失败", sendException);
            }
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
