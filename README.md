# 微信 iLink LLM 机器人

Spring Boot + `wechat-ilink-sdk` + DeepSeek 的微信机器人示例，支持接收文本、调用 DeepSeek 并回复，以及接收并保存图片。

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

图片与语音模型使用同一个阿里云百炼 Key：`qwen3-vl-flash`、`paraformer-v2`、`cosyvoice-v3.5-flash`。也可以直接把 Key 填入被 Git 忽略的 `src/main/resources/application-local.yml`：

```yaml
dashscope:
  api-key: "sk-你的百炼APIKey"
```

发送普通图片时，机器人会调用千问进行图片理解。发送 `语音：你的问题` 时，机器人会返回文字答案和 CosyVoice 合成的 WAV 音频文件。

微信收到的语音是 SILK 格式，而 `paraformer-v2` 只接受公网 HTTP/HTTPS 地址上的常见音频格式。项目已完成语音下载与 Paraformer 调用服务，完整的“收到微信语音后自动识别”还需要部署时配置 SILK 转 WAV 和公网临时文件地址。

可选配置：`WECHAT_DOWNLOAD_DIR` 指定收到的图片保存目录，默认是 `downloads`。

## 当前能力

- 微信扫码登录及消息监听
- 文本消息调用 LLM 后自动回复
- 图片消息下载到本地并回复接收结果
- 未设置 DeepSeek API Key 时返回明确配置提示
- 应用关闭时释放 SDK 和线程资源

语音收发可在下一阶段基于 SDK 的 `sendVoice` 和 `downloadVoiceFromMessageItem` 接口补充。
