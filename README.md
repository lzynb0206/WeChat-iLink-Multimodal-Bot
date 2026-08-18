# 微信 iLink 多模态机器人

基于 Spring Boot、微信 iLink SDK、阿里云百炼和心知天气 API 的微信机器人。

## 项目结构

```text
src/main/java/com/example/demo
├── DemoApplication.java
├── config
│   ├── AiConfig.java
│   └── WeatherConfig.java
├── controller
│   ├── CmdController.java
│   └── HelloController.java
├── model
│   ├── ActionType.java
│   ├── IntentResult.java
│   ├── ReplyMode.java
│   └── WeatherInfo.java
└── service
    ├── ai/AlibabaAiService.java
    ├── audio/AudioTranscoder.java
    ├── weather/WeatherService.java
    └── wechat/WechatBotService.java
```

## 模型和 API

| 功能 | 服务或模型 |
| --- | --- |
| 普通聊天 | 阿里云百炼 `qwen-flash` |
| 意图识别 | 阿里云百炼 `qwen-flash` |
| 图片理解 | 阿里云百炼 `qwen3-vl-flash` |
| 图片生成 | 阿里云百炼 `qwen-image-2.0` |
| 语音识别 | 阿里云百炼 `qwen3-asr-flash` |
| 语音合成文件 | 阿里云百炼 `cosyvoice-v3-flash` + `longanyang` |
| 实况天气 | 心知天气 V3 API |

## 配置 API Key

心知天气需要先在 https://www.seniverse.com/signup 注册账号，再到控制台的“产品管理”添加 API 产品。代码配置中需要填写产品密钥里的私钥 `key`，不是公钥 `uid`。

推荐在终端设置环境变量：

```bash
export DASHSCOPE_API_KEY="你的阿里云百炼API Key"
export SENIVERSE_API_KEY="你的心知天气私钥"
./mvnw spring-boot:run
```

也可以在被 Git 忽略的 `src/main/resources/application-local.yml` 中填写：

```yaml
dashscope:
  api-key: "sk-你的阿里云百炼API Key"

weather:
  api-key: "你的心知天气私钥"
```

不要把真实 Key 写入 `application.yaml`，也不要提交 `application-local.yml`。

## 使用方式

- `你好`：文字聊天，返回文字。
- `用语音介绍一下杭州`：返回 WAV 音频文件。
- 直接发送一段微信语音：ASR 识别后默认返回 WAV 音频文件。
- `生成一张雨中的杭州西湖`：生成并返回图片。
- `杭州今天天气怎么样`：查询心知天气并返回文字。
- `用语音告诉我上海天气`：查询天气并返回 WAV 音频文件。
- 直接发送图片：使用千问视觉模型理解图片。

## 语音编解码

入站链路：`微信 SILK → PCM → WAV → qwen3-asr-flash → 文字`。

出站链路：`回答文字 → CosyVoice WAV → 微信文件消息`。

机器人不会发送微信语音气泡，而是把 CosyVoice 生成的 WAV 作为文件直接发送。只有收到用户的微信语音时才需要进行 SILK → PCM → WAV 转换。SILK 和 PCM 临时文件创建在系统临时目录中，并在每次处理结束时删除；收到的语音不会永久保存到项目中。

项目内置的 `tools/macos-arm64/silk_codec` 仅适用于 Apple Silicon Mac。部署到其他操作系统时，需要替换相应平台的编解码器，并设置 `SILK_CODEC_PATH`。

## 启动

项目需要 Java 21：

```bash
./mvnw clean test
./mvnw spring-boot:run
```

启动后扫描项目根目录生成的 `wechat-login-qr.png`。二维码过期后重启应用即可重新生成。
