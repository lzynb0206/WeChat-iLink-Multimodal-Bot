# 微信 iLink LLM 机器人

Spring Boot + `wechat-ilink-sdk` 的微信机器人示例，支持接收文本、调用兼容 OpenAI Chat Completions 的大模型并回复，以及接收并保存图片。

## 启动

需要 Java 21。密钥只通过环境变量配置：

```bash
export LLM_API_KEY="你的 API Key"
export LLM_API_URL="https://api.openai.com/v1/chat/completions"
export LLM_MODEL="gpt-4o-mini"
export WECHAT_BOT_ENABLED="true"
./mvnw spring-boot:run
```

控制台会输出微信登录二维码的内容。将其渲染成二维码并扫码后，机器人开始接收消息。

可选配置：`WECHAT_DOWNLOAD_DIR` 指定收到的图片保存目录，默认是 `downloads`。

## 当前能力

- 微信扫码登录及消息监听
- 文本消息调用 LLM 后自动回复
- 图片消息下载到本地并回复接收结果
- 未设置 API Key 时返回明确配置提示
- 应用关闭时释放 SDK 和线程资源

语音收发可在下一阶段基于 SDK 的 `sendVoice` 和 `downloadVoiceFromMessageItem` 接口补充。
