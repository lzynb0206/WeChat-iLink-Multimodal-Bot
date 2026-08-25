# WeChat iLink Multimodal Bot

一个基于 Spring Boot、微信 iLink SDK 和阿里云百炼构建的多模态微信机器人。项目支持文本聊天、图片理解、图片生成、微信语音识别、WAV 语音文件回复、实时天气、联网新闻、文本翻译、Function Calling、自定义 Skill 和关键词 RAG。

## 功能概览

| 能力 | 实现方式 | 状态 |
| --- | --- | --- |
| 微信登录 | iLink SDK + 本地二维码 | 已完成 |
| 文本收发 | iLink SDK | 已完成 |
| 普通聊天 | `qwen-flash` | 已完成 |
| 意图识别 | `qwen-flash` 输出结构化 JSON | 已完成 |
| 图片理解 | `qwen3-vl-flash` | 已完成 |
| 图片生成 | `qwen-image-2.0` | 已完成 |
| 微信语音识别 | npm `silk-wasm` + `qwen3-asr-flash` | 已完成 |
| 语音合成 | `cosyvoice-v3-flash`，以 WAV 文件发送 | 已完成 |
| 实况天气 | 心知天气 API | 已完成 |
| 联网新闻 | `qwen-plus` + 百炼联网搜索 | 已完成 |
| 文本翻译 | `qwen-mt-flash` | 已完成 |
| Function Calling | 工具注册、参数校验、多轮调用 | 已完成 |
| 多步工具链 | 天气查询 → 温度换算 | 已完成 |
| 自定义 Skill | 每日简报：天气 + 新闻固定工作流 | 已完成 |
| 关键词 RAG | 本地 JSON 知识库检索与 Prompt 增强 | 已完成 |
| 分层消息路由 | Skill → RAG → LLM | 已完成 |

## 技术栈

- Java 21
- Spring Boot 4
- 微信 iLink SDK 2.3.3
- 阿里云百炼兼容模式与原生 API
- 心知天气 V3 API
- Node.js 18+
- npm `silk-wasm` 3.7.1
- Jackson、Lombok、ZXing
- Maven、JUnit 5

## 项目结构

```text
.
├── package.json                         # Node.js 音频依赖和检查命令
├── package-lock.json                    # npm 依赖锁文件
├── pom.xml                              # Maven 配置
├── scripts
│   ├── audio-self-test.mjs              # npm 解码链路自检
│   └── decode-silk.mjs                  # SILK → PCM → WAV 解码脚本
├── src/main/java/com/example/demo
│   ├── DemoApplication.java             # Spring Boot 入口
│   ├── config
│   │   ├── AiConfig.java                # 百炼模型和接口配置
│   │   ├── AudioConfig.java             # Node 解码器配置
│   │   ├── DailyBriefSkillConfig.java   # 每日简报默认参数
│   │   ├── RagConfig.java               # RAG 开关和知识库配置
│   │   └── WeatherConfig.java           # 心知天气配置
│   ├── model
│   │   ├── ActionType.java              # 用户动作类型
│   │   ├── IntentResult.java            # 意图识别结果
│   │   ├── MessageRouteResult.java       # 消息路由执行结果
│   │   ├── MessageRouteType.java         # Skill、RAG 或 LLM 路由来源
│   │   ├── ReplyMode.java               # 文本或语音文件回复
│   │   └── WeatherInfo.java             # 天气数据
│   ├── rag
│   │   ├── KeywordRagService.java        # 关键词评分、检索和 Prompt 增强
│   │   ├── KnowledgeDocument.java        # 知识文档模型
│   │   └── RagContext.java               # 检索上下文
│   ├── service
│   │   ├── ai/AlibabaAiService.java     # 聊天、视觉、生图、ASR、TTS
│   │   ├── ai/AlibabaToolService.java   # 新闻搜索和翻译模型客户端
│   │   ├── audio/AudioTranscoder.java   # Java 与 npm 解码进程通信
│   │   ├── routing/MessageRouter.java    # Skill → RAG → LLM 总路由
│   │   ├── weather/WeatherService.java  # 心知天气 HTTP 客户端
│   │   └── wechat/WechatBotService.java # 微信登录、消息处理和回复
│   ├── skill
│   │   ├── BotSkill.java                 # 自定义 Skill 统一接口
│   │   ├── SkillRegistry.java            # Skill 注册和关键词匹配
│   │   └── DailyBriefSkill.java          # 天气 + 新闻每日简报 Skill
│   └── tool
│       ├── BotTool.java                 # 工具统一接口
│       ├── ToolRegistry.java            # 工具注册与参数校验
│       ├── ToolCallingEngine.java       # 多轮工具调用引擎
│       ├── WeatherTool.java             # 天气工具
│       ├── CalculatorTool.java          # 精确计算工具
│       ├── TemperatureConverterTool.java# 温度换算工具
│       ├── NewsTool.java                # 联网新闻工具
│       └── TranslationTool.java         # 文本翻译工具
├── src/main/resources
│   ├── application.yaml                 # 公共配置和环境变量映射
│   └── rag/knowledge-base.json           # 本地 RAG 知识库
└── src/test/java/com/example/demo       # Spring 与工具单元测试
```

项目只通过微信 iLink SDK 接收和发送消息，不启动无关的 HTTP Controller。

## 快速开始

### 1. 环境要求

确认已经安装 Java、Node.js 和 npm：

```bash
java -version
node --version
npm --version
```

最低要求：Java 21、Node.js 18。

### 2. 安装 npm 音频依赖

```bash
npm install
npm run audio:check
```

检查成功时会输出：

```text
silk-wasm decoder is ready (... SILK bytes → ... WAV bytes)
```

### 3. 配置 API Key

推荐通过环境变量配置，避免把密钥提交到 Git：

```bash
export DASHSCOPE_API_KEY="你的阿里云百炼 API Key"
export SENIVERSE_API_KEY="你的心知天气私钥"
```

也可以创建不会被 Git 提交的 `src/main/resources/application-local.yml`：

```yaml
dashscope:
  api-key: "sk-你的阿里云百炼 API Key"

weather:
  api-key: "你的心知天气私钥"
```

心知天气需要填写产品的私钥 `key`，不是公钥 `uid`。

### 4. 测试并启动

```bash
./mvnw clean test
./mvnw spring-boot:run
```

启动后，项目根目录会生成 `wechat-login-qr.png`。使用微信扫描二维码，登录完成后即可向机器人发送消息。

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 空 | 阿里云百炼 API Key |
| `SENIVERSE_API_KEY` | 空 | 心知天气私钥 |
| `WECHAT_BOT_ENABLED` | `true` | 是否启动微信机器人 |
| `WECHAT_DOWNLOAD_DIR` | `downloads` | 收到图片后的保存目录 |
| `WECHAT_QR_CODE_PATH` | `wechat-login-qr.png` | 登录二维码位置 |
| `DASHSCOPE_CHAT_MODEL` | `qwen-flash` | 聊天模型 |
| `DASHSCOPE_INTENT_MODEL` | `qwen-flash` | 意图识别模型 |
| `DASHSCOPE_SEARCH_MODEL` | `qwen-plus` | 新闻联网搜索模型 |
| `DASHSCOPE_TRANSLATION_MODEL` | `qwen-mt-flash` | 专用翻译模型 |
| `DASHSCOPE_VISION_MODEL` | `qwen3-vl-flash` | 图片理解模型 |
| `DASHSCOPE_IMAGE_MODEL` | `qwen-image-2.0` | 图片生成模型 |
| `DASHSCOPE_ASR_MODEL` | `qwen3-asr-flash` | 语音识别模型 |
| `DASHSCOPE_TTS_MODEL` | `cosyvoice-v3-flash` | 语音合成模型 |
| `DASHSCOPE_TTS_VOICE` | `longanyang` | 语音合成音色 |
| `NODE_EXECUTABLE` | `node` | Node.js 命令或绝对路径 |
| `SILK_DECODER_SCRIPT` | `scripts/decode-silk.mjs` | npm SILK 解码脚本路径 |
| `SILK_SAMPLE_RATE` | `24000` | 解码输出采样率 |
| `SILK_DECODE_TIMEOUT_SECONDS` | `30` | 单次解码超时时间 |
| `RAG_ENABLED` | `true` | 是否启用关键词 RAG |
| `RAG_KNOWLEDGE_BASE` | `classpath:rag/knowledge-base.json` | RAG 知识库位置 |
| `RAG_MAX_RESULTS` | `3` | 最多注入的知识片段数，最大为 10 |
| `DAILY_BRIEF_DEFAULT_LOCATION` | `北京` | 每日简报默认城市 |
| `DAILY_BRIEF_DEFAULT_NEWS_TOPIC` | `人工智能` | 每日简报默认新闻主题 |
| `DAILY_BRIEF_NEWS_LIMIT` | `3` | 每日简报新闻条数，范围 1～10 |

## 消息处理流程

### 文本消息

```text
微信文本
  → WechatBotService
  → MessageRouter
  → Skill关键词命中？直接执行Skill并回复
  → RAG关键词命中？检索知识并增强Prompt
  → 都未命中？进入LLM意图识别和闲聊/工具调用
  → 文本或 WAV 文件回复
```

### 图片消息

```text
微信图片
  → iLink SDK 下载图片
  → qwen3-vl-flash 理解图片
  → 微信文本回复图片内容
```

收到的图片会保存在 `downloads` 目录。公开部署时建议改为对象存储，或者增加定期清理任务。

### 微信语音消息

```text
微信 SILK 字节
  → Java 写入 Node.js 标准输入
  → silk-wasm WebAssembly 解码为 PCM
  → Node.js 添加 WAV 文件头
  → WAV 字节从标准输出返回 Java
  → qwen3-asr-flash 识别文字
  → Skill / RAG / LLM 分层路由
  → CosyVoice 生成 WAV 回复文件
```

项目不再携带 `silk_codec` 本地可执行文件，也不再根据 macOS、Linux 或 CPU 架构维护不同二进制。Java 和 Node.js 通过标准输入输出传输音频，解码过程中不会创建 SILK、PCM 或 WAV 临时文件。

需要注意：npm 方案仍然会在运行机器上通过 WebAssembly 完成音频计算，只是不依赖平台相关的本地可执行文件。

## Skill 与 RAG

### Tool、Skill、RAG 分别解决什么问题？

| 机制 | 主要职责 | 谁决定是否执行 | 本项目示例 |
| --- | --- | --- | --- |
| Tool | 提供单一、可复用的原子能力 | LLM 根据 Function Calling 描述选择 | 天气、新闻、翻译、计算 |
| Skill | 固化一个高频业务流程，可组合多个 Tool | Java 关键词路由直接命中 | 每日简报同时执行天气和新闻 |
| RAG | 为模型补充外部或项目私有知识 | Java 先检索，命中后增强 Prompt | 配置、语音链路、RAG/Skill 项目知识 |

现有 Tool 并不是“不满足条件”。它们适合参数开放、组合方式不固定的任务，但高频固定流程如果每次都让模型重新规划，会增加模型判断、Token 消耗和结果不确定性。Skill 把已知流程固化，能让系统表现得更稳定、更快、更可控；它提升的是应用层的任务执行能力，并不会直接提升大模型本身的推理能力。

RAG 的业务意义是让模型在回答前读取可维护的外部知识。模型不需要重新训练，只要修改知识库就能更新项目事实，还能减少凭记忆回答造成的幻觉。当前版本是教学用的关键词检索，不使用 Embedding 和向量数据库，因此实现简单、成本低，但对同义词、语义相似和长文档召回的处理能力有限。

### 完整路由顺序

```text
用户文本或ASR识别结果
  → 命中Skill关键词？
      → 是：SkillRegistry执行Skill → 直接回复
      → 否：继续
  → 命中RAG知识关键词？
      → 是：检索Top-K知识片段 → 增强Prompt → LLM回复
      → 否：继续
  → LLM意图识别与兜底
      → 图片生成 / Function Calling / 普通闲聊
```

Skill 优先于 RAG。例如一句话同时包含“每日简报”和“RAG”，会先执行每日简报，不会进入知识问答。这样可以保证明确业务指令优先于开放式问答。

### 自定义每日简报 Skill

`DailyBriefSkill` 监听“生成每日简报”“今日简报”“每日简报”，并行调用天气与新闻 Tool，然后按照固定格式组合结果。推荐使用明确参数：

```text
生成每日简报 城市=上海，主题=大模型
```

城市或主题未提供时，使用配置中的默认值。单个工具失败时，简报会显示对应部分的错误，另一个工具仍能正常返回。

新增 Skill 时，实现 `BotSkill`、声明唯一名称与关键词，并添加 `@Component`。`SkillRegistry` 会自动注册；当多个关键词同时命中时，优先使用更长、更具体的关键词。

### 极简关键词 RAG

知识库存放在 `src/main/resources/rag/knowledge-base.json`。每条文档包含 `id`、`title`、`keywords` 和 `content`。检索过程如下：

```text
用户问题标准化
  → 检查每篇文档的keywords
  → 按命中关键词长度累计分数
  → 分数降序选取Top-K文档
  → 拼接为<retrieved_knowledge>
  → 与原问题一起发送给LLM
```

开启和关闭 RAG 的对比方式：

```bash
RAG_ENABLED=true ./mvnw spring-boot:run
# 向机器人发送：RAG是什么，它在这个项目中怎么实现？

RAG_ENABLED=false ./mvnw spring-boot:run
# 再发送同一句话，对比回答是否包含项目知识和“知识来源：本地RAG知识库”
```

自动测试也覆盖了同一句问题在开启时命中 RAG、关闭时回退到直接 LLM 路由的行为。

## Function Calling

模型不能直接执行 Java 方法。项目把每个工具的名称、说明和 JSON Schema 发送给模型，由模型返回工具名称与参数，再由 Java 执行真实工具。

```text
用户问题
  → qwen-flash 选择工具并生成参数
  → ToolRegistry 校验并执行 BotTool
  → 工具结果作为 role=tool 加回消息
  → qwen-flash 生成最终中文回答
```

`ToolCallingEngine` 最多允许 6 轮模型调用、每轮最多执行 8 个工具，避免模型无限循环或一次创建过多任务。工具名称、参数 JSON 长度、Schema 类型和重复注册都会在 Java 侧校验。

### 多工具协作

同一句话可以触发多个工具，执行方式由工具之间是否存在数据依赖决定：

| 场景 | 调用方式 | 示例 |
| --- | --- | --- |
| 参数互不依赖 | 同一轮返回多个 `tool_calls`，使用 Java 21 虚拟线程并行执行 | 同时查询上海和杭州天气 |
| 后一步依赖前一步结果 | 每轮只执行当前可运行的工具，把结果交回模型后再执行下一步 | 查询天气，再把实际温度换算成华氏度 |

并行执行完成后，结果仍按照模型返回的 `tool_calls` 顺序加入消息，并通过各自的 `tool_call_id` 与调用一一对应。单个工具发生业务异常时会返回结构化错误结果，其他独立工具仍可正常完成。新增的 `BotTool` 应保持无共享可变状态，或者自行保证线程安全。

```text
用户：查询上海和杭州天气，同时计算 123.45 × 67.89
  → 模型同一轮生成 3 个 tool_calls
  → get_current_weather(上海) ┐
  → get_current_weather(杭州) ├─ 并行执行
  → calculate(...)            ┘
  → 汇总 3 个真实结果后生成一次最终回答
```

存在依赖的调用继续使用多轮串行方式：

```text
查询张家港天气，并把温度换算成华氏度
  → get_current_weather(location="张家港")
  → 得到 temperature_celsius
  → convert_temperature(value=实际温度, from_unit="C", to_unit="F")
  → 生成最终回答
```

### 已接入工具

| 工具名称 | Java 类 | 用途 |
| --- | --- | --- |
| `get_current_weather` | `WeatherTool` | 查询指定城市或区县的实时天气 |
| `calculate` | `CalculatorTool` | 使用 `BigDecimal` 完成精确加减乘除 |
| `convert_temperature` | `TemperatureConverterTool` | 在摄氏度、华氏度、开尔文之间换算 |
| `search_news` | `NewsTool` | 通过百炼联网搜索查询带来源链接的近期新闻 |
| `translate_text` | `TranslationTool` | 使用 `qwen-mt-flash` 进行多语言文本翻译 |

### 工具扩展清单

以下清单同时展示已经注册的工具和后续路线图。未标记“已接入”的项目不会注册到模型工具列表中。

| 分类 | 建议工具名 | 主要参数 | 状态 | 接入注意事项 |
| --- | --- | --- | --- | --- |
| 天气 | `get_current_weather` | `location` | 已接入 | 心知天气 API |
| 新闻 | `search_news` | `query`、`limit` | 已接入 | 百炼联网搜索，返回来源和链接 |
| 翻译 | `translate_text` | `text`、`source_language`、`target_language` | 已接入 | 阿里云百炼 `qwen-mt-flash` |
| 世界时间 | `get_world_time` | `timezone` | 待接入 | 可优先使用 Java `ZoneId`，不需要外部 Key |
| IP 查询 | `lookup_ip` | `ip` | 待接入 | 校验 IPv4/IPv6，避免把内网地址发送给第三方 |
| 网络搜索 | `web_search` | `query`、`limit` | 待接入 | 需要搜索 API Key、来源链接和超时控制 |
| 汇率 | `convert_currency` | `amount`、`from`、`to` | 待接入 | 返回汇率时间，不能把结果描述成实时交易报价 |
| 菜谱 | `search_recipe` | `ingredients`、`preferences` | 待接入 | 区分模型生成内容和真实菜谱数据源 |
| 电影 | `search_movie` | `title`、`year` | 待接入 | 需要处理同名影片和版权数据来源 |
| 待办 | `manage_todo` | `action`、`content`、`id` | 待接入 | 需要数据库、用户隔离和持久化策略 |

### 新增工具

1. 在 `com.example.demo.tool` 新建类并实现 `BotTool`。
2. 添加 `@Component`，让 Spring 自动注册。
3. 提供唯一的英文工具名、清晰说明和 JSON Schema。
4. 在 `execute(JsonNode arguments)` 中再次校验所有外部参数。
5. 返回结构化 JSON，方便模型或后续工具继续使用。
6. 添加成功、参数错误和第三方失败的单元测试。

工具不需要手工加入 `if/else`。`ToolRegistry` 会自动收集所有 `BotTool` 实现。

## 使用示例

- `你好，简单介绍一下你自己`
- `分析一下我发的这张图片`
- `生成一张雨中的杭州西湖`
- `张家港今天天气怎么样`
- `计算 123.45 × 67.89`
- `查询上海天气，并把温度换算成华氏度`
- `查询今天的人工智能新闻，返回 3 条并附来源`
- `把“你好，世界”翻译成英文`
- `生成每日简报 城市=上海，主题=大模型`
- `RAG是什么，它在这个项目中怎么实现？`
- `API Key应该配置在哪里？`
- `用语音介绍一下杭州`
- 直接发送一段微信语音

机器人不会发送微信语音气泡。语音回复统一以 `qwen-answer.wav` 文件发送。

## 数据与文件

| 数据 | 保存位置 | 是否自动清理 |
| --- | --- | --- |
| 微信登录二维码 | `wechat-login-qr.png` | 重启时覆盖 |
| 收到的图片 | `downloads` | 否 |
| 微信 SILK 语音 | 仅在内存和进程管道中 | 请求结束后释放 |
| 解码后的 WAV | Java `byte[]` | 请求结束后释放 |
| TTS WAV | Java `byte[]`，随后发送微信 | 请求结束后释放 |
| API Key | 环境变量或 `application-local.yml` | 不应提交 Git |

## 测试

```bash
npm run audio:check
./mvnw clean test
```

提交代码前建议在微信端验证：

- 普通文本回复
- 图片理解和图片生成
- 微信 SILK 语音识别
- WAV 文件形式的语音回复
- 天气、计算器、温度换算、联网新闻和文本翻译
- 天气 → 温度换算的多步工具调用
- 每日简报 Skill 的天气、新闻组合结果
- RAG 开启时命中知识库，关闭时回退 LLM
- 同时包含 Skill 与 RAG 关键词时优先执行 Skill
- API Key 缺失和第三方 API 异常提示

## 常见问题

### 启动后二维码在哪里？

默认在项目根目录的 `wechat-login-qr.png`。可以通过 `WECHAT_QR_CODE_PATH` 修改。

### 提示找不到 `silk-wasm` 怎么办？

在项目根目录执行：

```bash
npm install
npm run audio:check
```

### 提示无法启动 Node.js 怎么办？

确认 `node --version` 可以执行。如果 Node 不在系统 PATH 中，用 `NODE_EXECUTABLE` 设置绝对路径。

### 天气提示地点不存在怎么办？

尽量只传可独立查询的城市或区县名，例如使用“张家港”，不要拼接成“苏州张家港”。同时确认心知天气配置的是私钥 `key`。

### 为什么不再提供 HTTP 接口？

当前项目的入口是微信 iLink SDK。演示性质的 `/hello`、`/status` 等接口与机器人核心功能无关，因此没有保留，也不启动额外的 Web MVC 服务器。

## 安全建议

- 不要提交真实 API Key、二维码、下载图片或本地配置。
- 工具参数始终视为不可信输入，并在 Java 侧校验。
- Skill 关键词和 RAG 文档内容需要经过代码审查，避免把不可信指令直接注入系统 Prompt。
- 为第三方 HTTP API 配置连接和读取超时。
- 新闻、搜索、电影等工具应使用有授权的数据源。
- 待办工具上线前必须实现微信用户隔离和访问控制。
- 生产环境应为 `downloads` 增加清理策略或使用对象存储。

## 第三方组件

SILK 解码使用 [`silk-wasm`](https://github.com/idranme/silk-wasm)，通过 npm 安装并以 WebAssembly 运行。具体声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
