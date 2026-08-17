# 微信 iLink LLM 机器人

Spring Boot + `wechat-ilink-sdk` + DeepSeek 的微信机器人示例，支持接收文本、调用 DeepSeek 并回复，以及接收并保存图片。

## 项目结构

```text
src/main/java/com/example/demo
├── DemoApplication.java              Spring Boot 启动入口
├── config
│   ├── LlmConfig.java                DeepSeek 配置
│   └── MediaAiConfig.java            阿里云百炼和 SILK 配置
├── controller
│   ├── CmdController.java            帮助、版本、状态接口
│   └── HelloController.java          Hello 示例接口
└── service
    ├── LlmService.java               DeepSeek 文本聊天
    ├── MediaAiService.java           图片理解、生图、ASR、TTS
    ├── AudioTranscoder.java          SILK 与 WAV 转换
    └── WechatBotService.java         微信登录和消息分发
```

代码按“配置、HTTP 接口、业务服务”分类，并为主要语句添加了中文学习注释。

## 启动

需要 Java 21。先在终端配置 DeepSeek API Key，再启动：

```bash
export DEEPSEEK_API_KEY="你的 DeepSeek API Key"
export DASHSCOPE_API_KEY="你的阿里云百炼 API Key"
./mvnw spring-boot:run
```

也可以在 IntelliJ IDEA 的运行配置中添加环境变量 `DEEPSEEK_API_KEY=你的Key`，然后运行 `DemoApplication`。

程序启动后会在项目根目录生成 `wechat-login-qr.png`，控制台同时输出它的绝对路径。用微信扫描该图片并确认登录后，机器人开始接收和回复消息。二维码会过期；过期后重启程序即可生成新的二维码。

默认使用 DeepSeek 官方的 `https://api.deepseek.com/chat/completions` 和 `deepseek-v4-flash`。需要使用 Pro 模型时设置 `DEEPSEEK_MODEL=deepseek-v4-pro`。

图片与语音模型使用同一个阿里云百炼 Key：`qwen3-vl-flash`、`qwen-image-2.0`、`qwen3-asr-flash`、`cosyvoice-v3.5-flash`。也可以直接把 Key 填入被 Git 忽略的 `src/main/resources/application-local.yml`：

```yaml
dashscope:
  api-key: "sk-你的百炼APIKey"
```

发送普通图片时，机器人会调用千问进行图片理解。发送 `生成图片：一只小猫` 时会调用千问生图并把图片发回微信。发送微信语音时，项目会把 SILK 转为 WAV、识别文字，再交给 DeepSeek 回复。发送 `语音：你的问题` 时，会把 CosyVoice 音频转成 SILK 语音回复。

项目内置的 SILK 编解码器位于 `tools/macos-arm64/silk_codec`，适用于当前 Apple Silicon Mac。部署到其他系统时需要提供对应平台的编解码器，并通过 `SILK_CODEC_PATH` 指定路径。

## 语音文件和内存说明

收到微信语音后的处理过程是：`微信 SILK → 内存 byte[] → 系统临时文件 → WAV byte[] → Base64 → qwen3-asr-flash`。

- SILK 和 PCM 临时文件由 `Files.createTempFile` 创建在 macOS 系统临时目录，转码结束后在 `finally` 中立即删除。
- 项目不会把收到的语音长期保存在 `downloads`。
- SILK、WAV 和 Base64 在识别期间会短暂占用 Java 内存；方法执行结束、对象不再被引用后会由垃圾回收器释放。
- 用户发送的图片仍会保存到项目的 `downloads`，需要时可以手动清理。

可选配置：`WECHAT_DOWNLOAD_DIR` 指定收到的图片保存目录，默认是 `downloads`。

## 当前能力

- 微信扫码登录及消息监听
- 文本消息调用 LLM 后自动回复
- 图片消息下载到本地并回复接收结果
- 未设置 DeepSeek API Key 时返回明确配置提示
- 应用关闭时释放 SDK 和线程资源

语音收发可在下一阶段基于 SDK 的 `sendVoice` 和 `downloadVoiceFromMessageItem` 接口补充。
