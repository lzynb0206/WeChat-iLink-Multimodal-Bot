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

---

## 项目背景与目标

这个项目不是简单地把一个聊天接口接到微信，而是完成一条多模态消息处理链路：

1. 使用微信 iLink SDK 登录并持续接收消息。
2. 判断用户希望聊天、生成图片、查询天气还是获得音频回复。
3. 根据意图调用对应的阿里云模型或天气接口。
4. 处理微信格式与模型接口格式之间的差异。
5. 把文字、图片或 WAV 文件重新发送给微信用户。

项目适合作为 Java 后端、多模态 AI、第三方 API 集成和问题排查能力的综合实践。

## 整体架构

```text
微信用户
   │
   ▼
wechat-ilink-sdk
   │
   ▼
WechatBotService ────── 图片消息 ──────► qwen3-vl-flash
   │
   ├── 文本/ASR结果 ──► qwen-flash 意图识别
   │                         │
   │                         ├── CHAT ──► qwen-flash
   │                         ├── IMAGE_GENERATION ──► qwen-image-2.0
   │                         └── WEATHER ──► 心知天气 API
   │
   └── replyMode=VOICE ──► CosyVoice ──► WAV 文件 ──► 微信
```

### 各层职责

| 类 | 职责 |
| --- | --- |
| `WechatBotService` | 微信登录、监听消息、分发任务、发送结果 |
| `AlibabaAiService` | 聊天、意图识别、图片理解、生图、ASR、TTS |
| `AudioTranscoder` | 微信 SILK 与标准 WAV 之间的入站转换 |
| `WeatherService` | 构造天气请求、解析结果、地点失败重试 |
| `AiConfig` | 管理百炼地址、API Key 和模型名称 |
| `WeatherConfig` | 管理心知天气地址和 API Key |
| `IntentResult` | 保存动作、回复形式、有效内容和地点 |

这种拆分遵循单一职责原则。微信逻辑不需要知道 HTTP 请求的具体 JSON，天气服务也不需要知道消息如何发送。

## 核心知识点

### 1. Spring Boot 配置绑定

`AiConfig` 和 `WeatherConfig` 使用 `@ConfigurationProperties` 把 YAML 配置绑定成 Java 对象。与在每个字段上使用 `@Value` 相比，它更适合管理一组相关配置，也便于更换模型或接口地址。

配置优先使用环境变量或被 Git 忽略的 `application-local.yml`，目的是让源代码与敏感信息分离。

### 2. 构造器依赖注入

业务类通过构造器接收其他服务，例如 `WechatBotService` 接收 AI、音频和天气服务。这样依赖关系是明确的，字段也可以声明为 `final`，更方便测试和维护。

### 3. REST API 调用

项目使用 `RestTemplate` 调用阿里云和心知天气：

1. 用 `HttpHeaders` 设置 `Content-Type` 和 Bearer Token。
2. 用 `Map` 组织请求 JSON。
3. 使用 `ObjectMapper` 解析响应 JSON。
4. 从固定 JSON 路径读取文字、图片地址或音频地址。
5. 对空响应和错误状态抛出业务含义明确的异常。

### 4. Base64 与 Data URL

图片和音频本来是二进制数据，但 JSON 只能直接保存文本。项目先对二进制执行 Base64 编码，再组装为 Data URL：

```text
data:audio/wav;base64,UklGRiQAAABXQVZF...
```

这样 ASR 可以直接读取内存里的 WAV，不需要把用户语音上传到公网服务器。

Base64 的代价是数据体积通常增加约三分之一，因此还要关注模型的文件大小限制。

### 5. PCM、WAV、MP3 与 SILK

| 格式 | 特点 | 本项目用途 |
| --- | --- | --- |
| PCM | 未压缩的原始采样数据，没有通用文件头 | SILK 编解码器的中间数据 |
| WAV | 通常是 WAV 文件头加 PCM，兼容性好、体积较大 | 发送给 ASR、接收 TTS 结果、作为微信文件回复 |
| MP3 | 有损压缩、文件较小 | 当前未使用，可作为以后节省流量的方案 |
| SILK | 面向语音通信的压缩编码 | 微信收到的语音格式 |

WAV 文件头会记录采样率、声道、位深以及数据长度。本项目生成的是 24 kHz、单声道、16-bit WAV。

### 6. 临时文件与内存管理

收到语音后，SILK 和 WAV 会短暂存在 `byte[]` 中。调用本地编解码器时，程序使用 `Files.createTempFile` 创建 SILK 和 PCM 临时文件，并通过 `finally` 删除。

这里有两个重要概念：

- `byte[]` 占用运行内存，对象失去引用后由 JVM 垃圾回收。
- 临时文件占用磁盘，必须显式删除，所以使用 `try/finally` 保证异常情况下也能清理。

项目不会长期保存收到的语音。用户发送的图片目前会保存到 `downloads`，便于开发阶段检查。

### 7. 意图识别不是关键词判断

第一版逻辑使用 `startsWith("生成图片")`、`startsWith("语音：")` 判断功能。这种方式只能识别固定句式，例如无法稳定理解“帮我画一只猫”或“用语音告诉我上海天气”。

当前实现让千问返回结构化 JSON：

```json
{
  "action": "WEATHER",
  "replyMode": "VOICE",
  "content": "今天上海天气怎么样",
  "location": "上海"
}
```

其中：

- `action` 决定执行聊天、生图还是天气查询。
- `replyMode` 决定返回文字还是 WAV 文件。
- `content` 是清理后的聊天或生图内容。
- `location` 是天气接口使用的地点。

动作和回复形式分开后，“用语音告诉我上海天气”才能同时表达天气查询和音频回复两个维度。模型调用失败时，项目还有本地关键词规则兜底。

## 真实问题与解决方案

以下问题均来自项目实际开发过程，适合面试前重点复习。

### 问题一：收到微信语音，但阿里云无法直接识别

**现象**

机器人能下载语音，却只能提示“微信语音为 SILK，需要转换为 WAV 并提供公网地址”。

**排查过程**

1. 确认微信 SDK 的 `downloadVoiceFromMessageItem` 返回的是二进制数据。
2. 检查文件格式，发现微信语音是 SILK，不是 ASR 常用的 WAV/MP3。
3. 查阅 ASR 请求格式，确认支持 Base64 WAV，不一定需要公网 URL。

**根因**

微信侧和模型侧使用的音频格式不同，缺少本地编解码步骤。

**解决方案**

引入本地 `silk_codec`，实现：

```text
SILK → PCM → 添加 WAV 文件头 → Base64 → qwen3-asr-flash
```

临时文件放在系统临时目录，并在 `finally` 中删除。最终日志能够输出“微信语音识别完成”。

**面试表达重点**

不要只说“接了一个 ASR 接口”，要说明真正的难点是消息平台和模型之间的格式适配，以及如何避免依赖公网文件地址。

### 问题二：TTS 模型与音色不匹配

**现象**

最初配置了 `cosyvoice-v3.5-flash` 和 `longanyang`，但这组配置不能直接正常使用。

**根因**

模型和音色不是任意组合。`cosyvoice-v3.5-flash` 不提供这项系统音色，而 `longanyang` 属于 `cosyvoice-v3-flash` 的音色。

**解决方案**

把默认配置调整为：

```yaml
tts-model: cosyvoice-v3-flash
tts-voice: longanyang
```

**经验总结**

第三方 AI 接口不仅要核对模型 ID，还要核对地域、音色、输入格式和返回格式。参数名称正确不代表参数组合有效。

### 问题三：语音气泡和音频文件不是同一种发送方式

**现象**

最初为了发送微信语音气泡，需要把 TTS 返回的 WAV 再编码成 SILK。后来需求明确为“不要语音气泡，只要文件”。

**解决方案**

删除出站 WAV → SILK 转换，改用：

```java
client.sendFile(fromUserId, wav, "qwen-answer.wav", null);
```

**经验总结**

同一段声音可以用“语音消息”或“普通文件”发送，但协议和用户体验不同。先确认产品需求，可以避免不必要的编解码和开发成本。

### 问题四：天气 Key 正确，但接口仍返回 404

**现象**

查询“苏州张家港天气”时出现：

```text
404 Not Found
status_code: AP010010
The location can not be found.
```

**排查过程**

1. 请求成功到达心知天气服务器，说明网络正常。
2. 如果 Key 错误，通常应是鉴权相关的 403，而日志是地点相关的 404。
3. 根据 `AP010010` 判断不是 Key 问题，而是地点字符串无法匹配。
4. 检查意图结果，发现可能把地点组合成“苏州张家港”，接口更容易识别“张家港”。

**根因**

自然语言里的行政区划名称和天气 API 支持的标准地点名称不完全一致。

**解决方案**

1. 修改意图提示词，要求返回最具体、可独立查询的区县名。
2. 明确加入示例：“苏州张家港”应提取为“张家港”。
3. 天气查询遇到 404 时，尝试行政区划切分和末尾区县名称。
4. 所有候选都失败后，向用户返回明确的地点提示，而不是错误地提示检查 Key。

**面试表达重点**

这个问题体现了根据 HTTP 状态和业务错误码缩小范围，而不是看到接口失败就盲目更换 API Key。

### 问题五：Spring Boot 4 启动时找不到 ObjectMapper Bean

**现象**

编译成功，但 Spring 容器测试失败：

```text
No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
```

**根因**

Spring Boot 4 默认自动配置的是新版 Jackson 3，而项目中的微信 SDK 间接带入了 Jackson 2。代码注入的是 Jackson 2 的 `com.fasterxml.jackson.databind.ObjectMapper`，Spring 容器中却没有对应 Bean。

**解决方案**

AI 和天气服务显式创建与当前代码兼容的 Jackson 2 `ObjectMapper`，避免把不同主版本的类型混在一起。

**经验总结**

类名相同不代表类型相同。排查依赖注入问题时，要看完整包名、依赖树和框架版本，而不是只看 `ObjectMapper` 这个类名。

### 问题六：调整包结构后出现重复 Bean

**现象**

Java 文件已经移动到新的包目录，但测试出现重复 Bean 定义。

**根因**

`target/classes` 中还残留着移动前编译的旧 `.class` 文件。普通增量编译没有完全清除旧产物，Spring 扫描到了新旧两个版本。

**解决方案**

执行：

```bash
./mvnw clean test
```

`clean` 先删除 `target`，再重新编译，重复 Bean 随之消失。

**经验总结**

源码已经删除不等于编译产物已经删除。重构包名、类名后出现异常时，应检查构建缓存和旧产物。

### 问题七：错误提示与真实原因不一致

**现象**

天气地点错误时，微信统一回复“请检查网络和 API Key”，容易误导排查。

**根因**

所有异常都被同一个兜底文案处理，丢失了业务错误的具体含义。

**解决方案**

对参数错误和业务状态错误保留可读信息；只有未知网络或系统错误才使用统一提示。

**经验总结**

日志面向开发者，应保留堆栈；用户提示面向用户，应准确但不能泄露 API Key、请求体等敏感信息。

## 问题排查方法

遇到故障时可以按以下顺序处理：

1. **先确认现象**：是没有收到消息、没有调用模型，还是结果发送失败。
2. **找到第一条异常日志**：不要只看最后一行统一错误提示。
3. **判断故障层级**：微信 SDK、本地转换、阿里云、天气 API 或返回消息。
4. **读取 HTTP 状态和业务错误码**：401/403 通常偏向鉴权，404 不一定是接口不存在，也可能是业务资源不存在。
5. **检查实际输入格式**：模型名、音色、地点、MIME、采样率和 JSON 路径。
6. **做最小验证**：例如单独验证 1 秒 PCM → SILK → PCM，而不是每次都启动完整机器人。
7. **修复后做回归测试**：运行 `./mvnw clean test`，再分别测试文字、图片、语音和天气。
8. **把问题写进文档**：记录现象、证据、根因和最终方案，避免以后重新踩坑。

## 测试清单

提交代码前至少执行：

```bash
./mvnw clean test
```

微信端手工测试：

- [ ] 普通文本可以返回文字。
- [ ] “用语音回答”可以收到并下载 WAV 文件。
- [ ] 发送微信语音后可以完成 ASR，并收到 WAV 回复文件。
- [ ] “生成一张……”可以收到图片。
- [ ] 直接发送图片可以收到图片描述。
- [ ] “张家港天气怎么样”可以返回实况天气。
- [ ] “用语音告诉我上海天气”可以返回天气 WAV 文件。
- [ ] API Key 缺失时提示明确且日志中不泄露 Key。
- [ ] 应用关闭后微信客户端和线程能够释放。

## 当前限制与后续优化

1. 当前消息处理运行在微信 SDK 的消息调度线程中，ASR、LLM、TTS 连续调用可能占用较长时间。后续可以加入独立线程池和消息队列，避免一条慢请求阻塞后续消息。
2. `RestTemplate` 目前没有统一配置连接超时、读取超时和有限次数重试，生产环境应补充。
3. 当前没有保存多轮对话上下文，每次聊天都是独立请求。
4. 地点纠错使用模型提取和字符串候选，未来可以先调用标准城市搜索接口并缓存城市 ID。
5. 图片会保存到 `downloads`，生产环境应增加定期清理策略或改为对象存储。
6. 当前内置 SILK 编解码器只支持 Apple Silicon Mac，部署到 Linux 时需要准备对应二进制文件。
7. 目前测试主要验证 Spring 容器启动，后续应使用 Mock 服务增加意图、天气解析和异常分支单元测试。
8. 第三方 API 有价格、限流和模型下线风险，生产环境应增加用量监控和降级策略。

## 面试快速复习

### 30 秒项目介绍

这是一个基于 Spring Boot 和微信 iLink SDK 的多模态 AI 机器人。我负责打通微信消息接收、阿里云千问聊天与意图识别、图片理解与生成、ASR、TTS 以及心知天气查询。项目的主要难点不是单纯调用模型，而是处理微信 SILK 与 WAV 的格式转换、使用结构化意图路由不同能力、兼容第三方依赖版本，以及根据业务错误码定位天气地点匹配问题。

### 面试官可能继续追问

**为什么不用关键词做意图识别？**

关键词适合少量固定命令，但无法覆盖自然表达和组合意图。项目使用模型输出结构化 JSON，并保留本地规则兜底，在灵活性和可用性之间做平衡。

**为什么语音识别要转 WAV？**

微信下载的是 SILK，而 ASR 接口支持 WAV 等通用格式。先解码为 PCM，再添加 WAV 文件头，可以直接以 Base64 发送给模型，不需要公网文件服务器。

**如何保证临时文件不会越积越多？**

临时文件在 `try` 中使用，在 `finally` 中调用 `Files.deleteIfExists`。即使转码或网络请求异常，也会执行清理。

**天气查询失败时是怎么定位的？**

先看 HTTP 状态和心知业务错误码。日志显示 `AP010010`，官方含义是地点不存在，而不是 Key 错误。因此继续检查传入的地点，最终发现“苏州张家港”需要规范为“张家港”。

**项目如何保护 API Key？**

真实 Key 只放环境变量或被 `.gitignore` 排除的 `application-local.yml`，公开配置中只保留变量引用。日志和异常提示不打印 Key。

**如果要支持更多并发，你会怎么改？**

把 SDK 回调中的耗时业务提交到有界线程池或消息队列；为每个外部请求设置超时、重试和熔断；按用户维护上下文顺序；对天气城市 ID 等稳定数据增加缓存。

## 开发记录

### 2026-08-17

- 创建并整理 Spring Boot 项目。
- 接入微信 iLink SDK，实现扫码登录和文本消息收发。
- 接入文本模型、图片理解、图片生成、ASR 和 TTS 的基础调用。
- 完成 SILK、PCM、WAV 转换方案。

### 2026-08-18

- 清理教学阶段的逐行注释并重新整理包结构。
- 将所需 AI 能力统一到阿里云百炼。
- 实现微信语音识别和 WAV 文件形式的语音合成回复。
- 将意图拆分为动作与回复方式，支持组合指令。
- 接入心知天气实况 API。
- 修复复合行政区名称导致的 `AP010010` 地点查询失败。
- 完成项目知识文档、问题复盘和面试速查内容。
