// service 包负责微信机器人的主要业务流程。
package com.example.demo.service;

// 二维码格式常量。
import com.google.zxing.BarcodeFormat;
// 把二维码矩阵写成 PNG 图片。
import com.google.zxing.client.j2se.MatrixToImageWriter;
// 二维码由黑白点组成，BitMatrix 保存这些点。
import com.google.zxing.common.BitMatrix;
// 根据登录文本生成二维码矩阵。
import com.google.zxing.qrcode.QRCodeWriter;
// 微信 iLink SDK 的主客户端。
import com.github.wechat.ilink.sdk.ILinkClient;
// 监听微信登录成功或失败。
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
// 监听微信收到的新消息。
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
// 保存登录成功后的机器人信息。
import com.github.wechat.ilink.sdk.core.login.LoginContext;
// 表示一条消息中的文字、图片或语音项目。
import com.github.wechat.ilink.sdk.core.model.MessageItem;
// 表示微信的一条完整消息。
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
// Lombok 自动创建 log 日志对象。
import lombok.extern.slf4j.Slf4j;
// DisposableBean 让应用关闭时执行资源清理。
import org.springframework.beans.factory.DisposableBean;
// @Value 从 application.yaml 读取单个配置值。
import org.springframework.beans.factory.annotation.Value;
// ApplicationReadyEvent 表示 Spring Boot 已经启动完成。
import org.springframework.boot.context.event.ApplicationReadyEvent;
// @EventListener 用来监听 Spring 事件。
import org.springframework.context.event.EventListener;
// @Service 把当前类交给 Spring 管理。
import org.springframework.stereotype.Service;

// Files 提供目录创建、图片保存等文件操作。
import java.nio.file.Files;
// Path 表示文件路径。
import java.nio.file.Path;
// List 保存 SDK 一次收到的多条消息。
import java.util.List;
// ExecutorService 负责在独立线程中执行微信登录。
import java.util.concurrent.ExecutorService;
// Executors 用来创建单线程执行器。
import java.util.concurrent.Executors;

// 自动创建日志对象。
@Slf4j
// 把微信机器人注册为 Spring 业务服务。
@Service
public class WechatBotService implements DisposableBean {

    // DeepSeek 文本聊天服务。
    private final LlmService llmService;
    // 阿里云图片和语音模型服务。
    private final MediaAiService mediaAiService;
    // 微信 SILK 与标准 WAV 之间的转换服务。
    private final AudioTranscoder audioTranscoder;
    // 微信 SDK 客户端，登录后负责收发消息。
    private ILinkClient client;
    // 微信登录线程执行器，避免登录流程阻塞 Spring 启动。
    private ExecutorService botExecutor;

    // 读取微信机器人是否启用；默认不启用可避免测试时扫码。
    @Value("${wechat.bot.enabled:false}")
    private boolean enabled;

    // 接收到的图片保存目录；语音不会保存到该目录。
    @Value("${wechat.bot.download-dir:downloads}")
    private String downloadDir;

    // 登录二维码 PNG 的保存位置。
    @Value("${wechat.bot.qr-code-path:wechat-login-qr.png}")
    private String qrCodePath;

    // Spring 通过构造器把三个业务服务传进来。
    public WechatBotService(LlmService llmService,
                            MediaAiService mediaAiService,
                            AudioTranscoder audioTranscoder) {
        // 保存 DeepSeek 服务。
        this.llmService = llmService;
        // 保存阿里云媒体模型服务。
        this.mediaAiService = mediaAiService;
        // 保存音频转码服务。
        this.audioTranscoder = audioTranscoder;
    }

    // Spring Boot 完全启动后自动执行该方法。
    @EventListener(ApplicationReadyEvent.class)
    public void initBot() {
        // 配置关闭机器人时不启动微信 SDK。
        if (!enabled) {
            // 在控制台说明如何启用机器人。
            log.info("微信机器人未启用；设置 WECHAT_BOT_ENABLED=true 后启动登录");
            // 提前结束方法。
            return;
        }
        // 创建只负责微信登录的单线程执行器。
        botExecutor = Executors.newSingleThreadExecutor(task -> {
            // 给线程取一个容易在日志中识别的名字。
            Thread thread = new Thread(task, "wechat-ilink-login");
            // 设置为守护线程，主程序关闭时它不会阻止 JVM 退出。
            thread.setDaemon(true);
            // 把创建好的线程交给执行器。
            return thread;
        });
        // 在独立线程中启动微信客户端和扫码登录。
        botExecutor.submit(this::startBot);
    }

    // 创建 SDK 客户端、注册监听器并发起扫码登录。
    private void startBot() {
        // 使用 Builder 模式创建微信 SDK 客户端。
        client = ILinkClient.builder()
                // 注册登录状态监听器。
                .onLogin(new OnLoginListener() {
                    // SDK 登录成功时回调该方法。
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        // 记录登录成功的机器人 ID。
                        log.info("微信机器人登录成功 botId={}", context.getBotId());
                    }

                    // SDK 登录失败时回调该方法。
                    @Override
                    public void onLoginFailure(Throwable throwable) {
                        // 记录完整异常，方便开发阶段排查。
                        log.error("微信机器人登录失败", throwable);
                    }
                })
                // 注册收到微信消息时的监听器。
                .onMessage(new OnMessageListener() {
                    // SDK 一次可能批量返回多条消息。
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        // 逐条处理收到的完整消息。
                        for (WeixinMessage message : messages) {
                            // 取出发送者微信用户 ID，回复时需要使用它。
                            String fromUserId = message.getFrom_user_id();
                            // 一条消息可能包含一个或多个消息项目。
                            List<MessageItem> itemList = message.getItem_list();
                            // 没有消息项目时跳过该消息。
                            if (itemList == null) {
                                // 继续处理下一条消息。
                                continue;
                            }
                            // 逐个检查文字、图片和语音项目。
                            for (MessageItem item : itemList) {
                                // text_item 不为空说明用户发送了文字。
                                if (item.getText_item() != null) {
                                    // 取出用户输入的文字。
                                    String userText = item.getText_item().getText();
                                    // 进入文本处理流程。
                                    handleText(fromUserId, userText);
                                }
                                // image_item 不为空说明用户发送了图片。
                                if (item.getImage_item() != null) {
                                    // 进入图片下载与理解流程。
                                    handleImage(fromUserId, item);
                                }
                                // voice_item 不为空说明用户发送了微信语音。
                                if (item.getVoice_item() != null) {
                                    // 进入 SILK 转码、识别和回复流程。
                                    handleVoice(fromUserId, item);
                                }
                            }
                        }
                    }
                })
                // 根据前面的配置真正创建客户端。
                .build();

        // 登录涉及网络和线程等待，因此需要捕获异常。
        try {
            // 请求微信登录二维码原始文本。
            String qrCodeContent = client.executeLogin();
            // 把原始文本转换为用户可以扫描的 PNG 文件。
            Path qrPath = writeQrCode(qrCodeContent);
            // 在控制台打印二维码绝对路径。
            log.info("微信登录二维码已生成：{}，请用微信扫码", qrPath);
            // 等待用户扫码并确认登录。
            LoginContext context = client.getLoginFuture().get();
            // 登录完成后再次打印机器人 ID。
            log.info("微信登录完成 botId={}", context.getBotId());
        } catch (InterruptedException exception) {
            // 恢复线程中断标记，遵守 Java 线程中断规范。
            Thread.currentThread().interrupt();
            // 记录登录线程被中断。
            log.warn("微信机器人登录线程被中断");
        } catch (Exception exception) {
            // 记录二维码请求或登录流程中的其他异常。
            log.error("微信机器人启动失败", exception);
        }
    }

    // 把 SDK 返回的登录内容生成二维码 PNG。
    private Path writeQrCode(String content) throws Exception {
        // 把配置路径转换为规范的绝对路径。
        Path target = Path.of(qrCodePath).toAbsolutePath().normalize();
        // 取得二维码文件的父目录。
        Path parent = target.getParent();
        // 父目录存在时确保目录已经创建。
        if (parent != null) {
            // 递归创建缺少的目录。
            Files.createDirectories(parent);
        }
        // 把登录文本编码成 360×360 的二维码黑白矩阵。
        BitMatrix matrix = new QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, 360, 360);
        // 把矩阵写成 PNG 文件。
        MatrixToImageWriter.writeToPath(matrix, "PNG", target);
        // 返回文件路径供日志显示。
        return target;
    }

    // 根据文本内容选择聊天、生图或语音回复功能。
    private void handleText(String fromUserId, String userText) {
        // 记录收到文本消息，但不在日志中打印隐私内容。
        log.info("收到微信文本消息 userId={}", fromUserId);
        // 微信发送和模型调用都可能失败，因此使用 try/catch。
        try {
            // 在微信中显示机器人正在输入。
            client.startTyping(fromUserId);
            // 判断用户是不是在请求生成图片。
            if (isImageRequest(userText)) {
                // 去掉“生成图片”等命令前缀，只保留描述。
                String prompt = imagePrompt(userText);
                // 调用千问生图并取得图片二进制。
                byte[] image = mediaAiService.generateImage(prompt);
                // 把生成的图片直接发回微信。
                client.sendImage(fromUserId, image, "qwen-image.png", prompt);
                // 生图完成后不再调用 DeepSeek。
                return;
            }
            // “语音：”开头表示用户希望收到语音气泡回答。
            boolean voiceReply = userText.startsWith("语音：") || userText.startsWith("语音:");
            // 语音命令需要去掉前三个字符，普通文本保持原样。
            String prompt = voiceReply ? userText.substring(3).trim() : userText;
            // 调用 DeepSeek 生成文字回答。
            String aiAnswer = llmService.chat(prompt);
            // 先把文字答案发给用户。
            client.sendText(fromUserId, aiAnswer);
            // 请求语音回复且百炼 Key 已配置时继续合成语音。
            if (voiceReply && mediaAiService.isConfigured()) {
                // CosyVoice 先把文字合成为 WAV，WAV 只暂存在内存。
                byte[] wav = mediaAiService.synthesizeSpeech(aiAnswer);
                // 把 WAV 转成微信支持的 SILK，临时文件会自动删除。
                byte[] silk = audioTranscoder.wavToSilk(wav);
                // 把 SILK 作为微信语音消息发送。
                client.sendVoice(fromUserId, silk, "deepseek-answer.silk",
                        // 根据 WAV 计算语音气泡应该显示的时长。
                        audioTranscoder.wavDurationMillis(wav),
                        // 告诉微信这段声音使用 24kHz 采样率。
                        24000);
            }
        } catch (Exception exception) {
            // 记录文本、生图或语音回复中的异常。
            log.error("处理文本消息失败 userId={}", fromUserId, exception);
            // 尝试把容易理解的失败提示发给用户。
            sendErrorMessage(fromUserId, "消息处理失败，请检查 API Key 或稍后重试。");
        } finally {
            // 无论成功失败都要停止“正在输入”状态。
            stopTypingQuietly(fromUserId);
        }
    }

    // 判断文字是否是生图命令。
    private boolean isImageRequest(String text) {
        // 支持四种常见中文表达。
        return text.startsWith("生成图片") || text.startsWith("生成一张")
                || text.startsWith("画一张") || text.startsWith("帮我画");
    }

    // 删除生图命令前缀，只保留真正的提示词。
    private String imagePrompt(String text) {
        // 使用正则表达式匹配支持的命令前缀并替换为空字符串。
        String prompt = text.replaceFirst(
                "^(生成图片[:：]?|生成一张|画一张|帮我画)", "").trim();
        // 用户没有提供描述时，给出一个简单的默认提示词。
        return prompt.isEmpty() ? "一只可爱的小猫" : prompt;
    }

    // 下载用户图片、保存图片并调用千问理解。
    private void handleImage(String fromUserId, MessageItem item) {
        // 文件和网络操作可能失败，因此捕获异常。
        try {
            // 把配置中的图片目录转换为绝对路径。
            Path directory = Path.of(downloadDir).toAbsolutePath().normalize();
            // 如果目录不存在就创建它。
            Files.createDirectories(directory);
            // 创建不重复的 JPG 文件名。
            Path target = Files.createTempFile(directory, "wechat-image-", ".jpg");
            // 从微信服务器下载并解密图片到内存。
            byte[] imageBytes = client.downloadImageFromMessageItem(item);
            // 把图片保存到 downloads，方便学生查看原始文件。
            Files.write(target, imageBytes);
            // 已配置百炼 Key 时调用图片理解。
            if (mediaAiService.isConfigured()) {
                // 让千问视觉模型描述图片内容。
                String answer = mediaAiService.understandImage(imageBytes, "image/jpeg");
                // 把理解结果发回微信。
                client.sendText(fromUserId, answer);
            } else {
                // 没有 Key 时只提示图片已经收到。
                client.sendText(fromUserId, "图片已收到。请配置 DASHSCOPE_API_KEY 后启用图片理解。");
            }
            // 在控制台显示图片保存位置。
            log.info("微信图片已保存至 {}", target);
        } catch (Exception exception) {
            // 记录图片下载、保存或理解异常。
            log.error("处理图片消息失败 userId={}", fromUserId, exception);
            // 向微信发送统一失败提示。
            sendErrorMessage(fromUserId, "图片处理失败，请稍后重试。");
        }
    }

    // 把微信语音转文字，再交给 DeepSeek 回答。
    private void handleVoice(String fromUserId, MessageItem item) {
        // 下载、转码和模型调用都可能失败，因此捕获异常。
        try {
            // 下载并解密微信 SILK 语音；此 byte[] 暂时占用内存。
            byte[] silk = client.downloadVoiceFromMessageItem(item);
            // 本地执行 SILK → PCM → WAV；临时文件在方法结束前删除。
            byte[] wav = audioTranscoder.silkToWav(silk);
            // 把 WAV 以 Base64 形式发给千问 ASR，不需要公网文件。
            String userText = mediaAiService.transcribeAudio(wav);
            // 只记录识别成功，不保存 SILK 或 WAV 到项目目录。
            log.info("微信语音识别完成 userId={}", fromUserId);
            // 把识别文字交给 DeepSeek 生成回答。
            String aiAnswer = llmService.chat(userText);
            // 同时告诉用户识别结果和 DeepSeek 回答。
            client.sendText(fromUserId, "你说：“" + userText + "”\n\n" + aiAnswer);
        } catch (Exception exception) {
            // 记录语音下载、转码、识别或回复异常。
            log.error("处理语音消息失败 userId={}", fromUserId, exception);
            // 向微信发送统一失败提示。
            sendErrorMessage(fromUserId, "语音处理失败，请稍后重试。");
        }
        // 方法结束后 silk 和 wav 没有引用，Java 垃圾回收器会释放其内存。
    }

    // 安静地停止微信“正在输入”状态。
    private void stopTypingQuietly(String fromUserId) {
        // SDK 网络请求可能失败，因此单独保护。
        try {
            // 通知微信停止输入状态。
            client.stopTyping(fromUserId);
        } catch (Exception exception) {
            // 输入状态失败不影响主要回答，只记录 debug 日志。
            log.debug("停止输入状态失败 userId={}", fromUserId, exception);
        }
    }

    // 尝试发送错误提示，避免错误处理本身再次中断消息线程。
    private void sendErrorMessage(String fromUserId, String message) {
        // 微信发送也可能失败，因此捕获异常。
        try {
            // 把简短错误原因发给用户。
            client.sendText(fromUserId, message);
        } catch (Exception exception) {
            // 只记录日志，不再继续抛出异常。
            log.debug("发送错误提示失败 userId={}", fromUserId, exception);
        }
    }

    // Spring 关闭应用时自动调用该方法释放资源。
    @Override
    public void destroy() {
        // 已创建微信客户端时关闭它的网络连接和线程。
        if (client != null) {
            // 调用 SDK 的资源释放方法。
            client.close();
        }
        // 已创建登录执行器时停止线程。
        if (botExecutor != null) {
            // 立即请求中断尚未完成的任务。
            botExecutor.shutdownNow();
        }
    }
}
