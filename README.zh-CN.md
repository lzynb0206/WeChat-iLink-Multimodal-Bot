<div align="center">

# 🤖 WeChat iLink 多模态机器人

**一个实用、易理解、可持续扩展的微信 AI 助手基础项目。**

将微信与千问多模态模型、工具调用、可复用 Skill 和本地知识库连接起来。

<p>
  <a href="./README.md">
    <img src="https://img.shields.io/badge/English-Read_Docs-2563EB?style=for-the-badge" alt="English README">
  </a>
  <a href="./README.zh-CN.md">
    <img src="https://img.shields.io/badge/简体中文-当前语言-DC2626?style=for-the-badge" alt="简体中文 README">
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.1.0">
  <img src="https://img.shields.io/badge/Node.js-18+-339933?style=flat-square&logo=nodedotjs&logoColor=white" alt="Node.js 18+">
  <img src="https://img.shields.io/badge/WeChat-iLink-07C160?style=flat-square&logo=wechat&logoColor=white" alt="WeChat iLink">
  <img src="https://img.shields.io/badge/Qwen-DashScope-615CED?style=flat-square" alt="Qwen on DashScope">
</p>

</div>

> 这个项目不只是一个聊天 Demo，同时也刻意保持了较小的规模，方便阅读和二次开发。你可以在此基础上构建个人助理、知识库机器人、客服流程、自动化 Agent，或自己的微信 AI 产品。

## ✨ 项目亮点

| | 能力 | 说明 |
| --- | --- | --- |
| 💬 | 微信接入 | 扫码登录，接收文本/图片/语音，发送文本/图片/WAV 文件 |
| 🧠 | 多模态 AI | 聊天、意图识别、图片理解、生图、ASR 和 TTS |
| 🛠️ | Function Calling | 参数 Schema 校验、多轮调用和结构化结果 |
| ⚡ | 并行工具 | 使用 Java 21 虚拟线程并行执行互不依赖的 Tool |
| 🧩 | 可复用 Skill | 将多个 Tool 组合为确定、稳定的业务流程 |
| 📚 | 本地 RAG | 从 JSON 知识库进行轻量级关键词检索 |
| 🌤️ | 实时信息 | 天气、联网新闻、翻译和精确计算 |
| 🚦 | 分层路由 | 按 **Skill → RAG → LLM/Tool** 的顺序处理消息 |

## 🧱 适合作为二次开发基础

项目已经包含 AI 助手所需的核心模块：

- 使用真实微信消息渠道，而不是模拟的 HTTP 接口。
- 完整的多模态输入与输出链路。
- 自动发现 `BotTool` 组件的工具注册机制。
- 用于固定业务流程的 Skill 注册机制。
- 可以进一步升级为向量检索的知识层。
- 配置管理、参数校验、异常处理和单元测试。

后续无需重写消息生命周期，可以随着需求逐步增加 Tool、Skill 和知识源。

## 🔄 工作流程

```text
文本 ───────────────────────────────┐
微信语音 → SILK → WAV → ASR 转文字 ─┼→ MessageRouter
                                    ├→ Skill 命中：执行确定性业务流程
                                    ├→ RAG 命中：向 Prompt 注入本地知识
                                    └→ LLM：意图识别、聊天或调用 Tool

微信图片 → 下载 → 视觉模型 → 文本回复
生图请求 → 图片生成模型 → 图片回复
语音回复 → TTS → WAV 文件回复
```

### 路由优先级

1. **Skill** 处理明确、可重复的固定流程。
2. **RAG** 使用项目私有知识回答问题。
3. **LLM/Tool** 处理开放式聊天、生图和动态工具选择。

这样既能保证已知流程稳定可控，也保留了大模型处理开放任务的灵活性。

## 🧰 内置能力

### Tool

| Tool | 用途 | 实现方式 |
| --- | --- | --- |
| `get_current_weather` | 查询城市或区县的实时天气 | 心知天气 |
| `search_news` | 查询包含来源和链接的近期新闻 | 百炼联网搜索 |
| `translate_text` | 多语言文本翻译 | 千问翻译模型 |
| `calculate` | 高精度加减乘除 | Java `BigDecimal` |
| `convert_temperature` | 摄氏度、华氏度和开尔文换算 | 本地 Java 代码 |

工具引擎支持两种协作方式：

- **并行执行：** 同一轮查询多个城市天气并完成计算。
- **依赖执行：** 先查询真实温度，再把返回值交给温度换算工具。

### Skill

内置的 `daily_brief` Skill 会并行执行天气和新闻工具，再组合成格式稳定的每日简报。

```text
生成每日简报 城市=杭州，主题=大模型
```

### 本地 RAG

当前 RAG 会对本地 JSON 知识库进行关键词评分，选择最相关的内容并注入模型 Prompt。它实现简单、容易理解，适合教学、原型和小型固定知识库。

## 🧑‍💻 技术栈

| 层级 | 技术 |
| --- | --- |
| 运行环境 | Java 21、Node.js 18+ |
| 应用框架 | Spring Boot 4.1.0、Spring Web |
| 微信接入 | `wechat-ilink-sdk` 2.3.3、ZXing |
| AI 服务 | 阿里云百炼兼容 API 与原生 API |
| 模型 | Qwen Chat/VL/Image/ASR/MT、CosyVoice TTS |
| 实时数据 | 心知天气、百炼联网搜索 |
| 音频 | `silk-wasm` 3.7.1、进程管道、WAV |
| 工程工具 | Maven、Jackson、Lombok、JUnit 5 |
| 并发 | Java 21 虚拟线程 |

## 🚀 快速开始

### 1. 环境准备

```bash
java -version   # 需要 Java 21+
node --version  # 需要 Node.js 18+
npm --version
```

安装 SILK 音频依赖并检查解码链路：

```bash
npm ci
npm run audio:check
```

### 2. 配置密钥

```bash
export DASHSCOPE_API_KEY="你的阿里云百炼 API Key"
export SENIVERSE_API_KEY="你的心知天气私钥"
```

`DASHSCOPE_API_KEY` 是 AI 功能的必需配置；`SENIVERSE_API_KEY` 只用于天气和每日简报。心知天气需要填写私钥 `key`，不是公钥 `uid`。

也可以创建已被 Git 忽略的 `src/main/resources/application-local.yml`：

```yaml
dashscope:
  api-key: "sk-..."

weather:
  api-key: "..."
```

### 3. 测试并启动

```bash
./mvnw test
./mvnw spring-boot:run
```

应用会在项目根目录生成 `wechat-login-qr.png`。使用微信扫码后，即可向机器人发送消息。

如果只想启动 Spring 容器而不登录微信：

```bash
WECHAT_BOT_ENABLED=false ./mvnw spring-boot:run
```

## 💬 使用示例

```text
你能做什么？
查询上海天气，并把温度换算成华氏度
查询今天的人工智能新闻，返回 3 条并附来源
把“你好，世界”翻译成英文
生成每日简报 城市=杭州，主题=大模型
RAG 是什么，它在这个项目中怎么实现？
生成一张雨中的西湖
用语音介绍一下杭州
```

## ⚙️ 常用配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `WECHAT_BOT_ENABLED` | `true` | 是否启动微信机器人 |
| `WECHAT_DOWNLOAD_DIR` | `downloads` | 接收图片的保存目录 |
| `WECHAT_QR_CODE_PATH` | `wechat-login-qr.png` | 登录二维码路径 |
| `RAG_ENABLED` | `true` | 是否启用本地关键词 RAG |
| `RAG_KNOWLEDGE_BASE` | `classpath:rag/knowledge-base.json` | 知识库位置 |
| `RAG_MAX_RESULTS` | `3` | 最多检索的知识文档数，范围 1～10 |
| `DAILY_BRIEF_DEFAULT_LOCATION` | `北京` | 每日简报默认城市 |
| `DAILY_BRIEF_DEFAULT_NEWS_TOPIC` | `人工智能` | 每日简报默认新闻主题 |
| `DAILY_BRIEF_NEWS_LIMIT` | `3` | 每日简报新闻条数，范围 1～10 |
| `NODE_EXECUTABLE` | `node` | Node.js 命令或绝对路径 |
| `SILK_DECODE_TIMEOUT_SECONDS` | `30` | SILK 解码超时时间 |

所有模型名称都可以通过对应的 `DASHSCOPE_*_MODEL` 环境变量替换。完整配置见 [`application.yaml`](src/main/resources/application.yaml)。

## 📁 项目结构

```text
src/main/java/com/example/demo
├── config/              # AI、天气、音频、RAG 和 Skill 配置
├── service/
│   ├── wechat/          # 登录、消息收发和媒体处理
│   ├── routing/         # Skill → RAG → LLM 路由
│   ├── ai/              # 百炼模型客户端
│   ├── audio/           # Java → Node.js SILK 解码
│   └── weather/         # 心知天气客户端
├── tool/                # Tool 接口、注册表、引擎和内置工具
├── skill/               # Skill 接口、注册表和每日简报
├── rag/                 # 关键词检索和 Prompt 增强
└── model/               # 意图、路由和天气模型

src/main/resources/
├── application.yaml
└── rag/knowledge-base.json

scripts/                 # SILK 解码和自检脚本
src/test/                # Tool、Skill、RAG 和路由测试
```

项目通过微信 iLink SDK 收发消息，目前不提供 HTTP API。

## 🧩 扩展指南

### 新增 Tool

1. 在 `tool/` 中实现 `BotTool`。
2. 提供唯一名称、功能说明和 JSON Schema。
3. 添加 `@Component`，`ToolRegistry` 会自动发现。
4. 校验所有外部参数并返回结构化结果。
5. 添加成功、非法参数和第三方异常测试。

Tool 可能被并行执行，因此实现应保持无状态或自行保证线程安全。

### 新增 Skill

实现 `BotSkill`、添加 `@Component`，并声明名称、关键词和执行流程。Skill 适合早报、行程助手、客服工单、报表生成等可重复的多工具任务。

### 扩展知识库

编辑 [`knowledge-base.json`](src/main/resources/rag/knowledge-base.json)：

```json
{
  "id": "unique-id",
  "title": "文档标题",
  "keywords": ["关键词1", "关键词2"],
  "content": "提供给模型的知识内容"
}
```

知识库会在应用启动时加载，修改后需要重启。

## 🌱 可以继续开发什么

- 👤 增加用户级记忆和定时提醒，构建个人微信助理。
- 🎧 接入产品知识库与工单流程，构建智能客服。
- 🏢 连接企业文档和内部 API，构建内部知识助手。
- 📅 接入日历、待办、邮件、天气和日报，构建效率机器人。
- 🔎 增加 Embedding、向量检索、重排和引用，升级语义 RAG。
- 🌐 抽象 AI Provider 与消息适配器，支持更多模型和消息渠道。
- 🛡️ 增加权限、审计、限流、重试和可观测性，升级为生产服务。

## ⚠️ 当前边界

- RAG 使用关键词匹配，暂不支持 Embedding 和复杂语义检索。
- 普通对话之间相互独立，目前没有持久化记忆或数据库。
- 收到的图片会保存到 `downloads/`，不会自动清理。
- 入站语音在内存中解码；语音回复以 WAV 文件发送，不是微信语音气泡。
- AI、天气和新闻功能依赖外部网络、密钥及服务可用性。

## ✅ 测试

```bash
npm run audio:check
./mvnw clean test
```

单元测试覆盖参数校验、并行和多轮工具调用、Skill 优先级、RAG 开关与消息路由。真实微信登录和外部 AI 服务仍建议进行端到端测试。

## 🔐 安全与第三方组件

不要提交 API Key、登录二维码、本地配置或下载的用户图片。仓库已忽略 `.env`、`application-local.yml`、`downloads/`、`wechat-login-qr.png` 和 `node_modules/`。

微信语音解码使用 MIT 许可的 [`silk-wasm`](https://github.com/idranme/silk-wasm)，详情见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
