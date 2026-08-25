# WeChat iLink Multimodal Bot

基于 Spring Boot、微信 iLink SDK 和阿里云百炼的多模态微信机器人。它可以处理文本、图片和微信语音，并通过 Tool、Skill 与本地 RAG 组合实时数据和项目知识。

## 已实现能力

| 类别 | 能力 |
| --- | --- |
| 微信接入 | 扫码登录、接收文本/图片/语音、发送文本/图片/WAV 文件 |
| 大模型 | 普通聊天、意图识别、图片理解、图片生成、ASR、TTS |
| 实时工具 | 心知天气、联网新闻、文本翻译、精确计算、温度换算 |
| 工具编排 | Function Calling、多轮调用、独立工具并行、依赖工具串行 |
| Skill | “每日简报”固定编排天气与新闻，并行执行后统一输出 |
| RAG | 从本地 JSON 知识库进行关键词检索并增强 Prompt |
| 消息路由 | 按 `Skill → RAG → LLM/Tool` 的优先级处理消息 |

示例消息：

```text
查询上海天气，并把温度换算成华氏度
查询今天的人工智能新闻，返回 3 条并附来源
把“你好，世界”翻译成英文
生成每日简报 城市=杭州，主题=大模型
RAG 是什么，它在这个项目中怎么实现？
生成一张雨中的西湖
用语音介绍一下杭州
```

## 处理流程

```text
文本 ───────────────────────────────┐
微信语音 → SILK 解码 → ASR 转文字 ──┼→ MessageRouter
                                    ├→ Skill 命中：直接执行固定流程
                                    ├→ RAG 命中：注入本地知识后调用 LLM
                                    └→ LLM：意图识别、聊天或 Function Calling

微信图片 → 下载并保存 → 视觉模型理解 → 文本回复
生图意图 → 图片生成模型 → 图片回复
语音回复 → TTS → WAV 文件回复
```

Tool 是天气、计算等单一能力；Skill 是由多个 Tool 组成的确定性业务流程；RAG 用于给模型补充可维护的外部知识。三者解决的问题不同，可以同时扩展。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 运行环境 | Java 21、Node.js 18+ |
| 应用框架 | Spring Boot 4.1.0、Spring Web |
| 微信接入 | `wechat-ilink-sdk` 2.3.3、ZXing 二维码 |
| AI 服务 | 阿里云百炼兼容 API 与原生 API |
| 模型 | Qwen Chat/VL/Image/ASR/MT、CosyVoice TTS |
| 实时数据 | 心知天气 V3、百炼联网搜索 |
| 音频 | npm `silk-wasm` 3.7.1、Java 进程管道、WAV |
| 工程工具 | Maven、Jackson、Lombok、JUnit 5 |
| 并发 | Java 21 虚拟线程，用于并行 Tool 和 Skill |

## 快速开始

### 1. 环境准备

```bash
java -version   # 需要 Java 21+
node --version  # 需要 Node.js 18+
npm --version
```

安装并检查微信 SILK 音频解码器：

```bash
npm ci
npm run audio:check
```

### 2. 配置密钥

```bash
export DASHSCOPE_API_KEY="你的阿里云百炼 API Key"
export SENIVERSE_API_KEY="你的心知天气私钥"
```

`DASHSCOPE_API_KEY` 是聊天和多模态能力的必需配置；`SENIVERSE_API_KEY` 只在天气及每日简报功能中使用。心知天气应填写私钥 `key`，不是公钥 `uid`。

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

启动后，根目录会生成 `wechat-login-qr.png`。使用微信扫码登录，然后直接向机器人发送消息。

如需仅启动 Spring 容器而不登录微信：

```bash
WECHAT_BOT_ENABLED=false ./mvnw spring-boot:run
```

## 常用配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `WECHAT_BOT_ENABLED` | `true` | 是否启动微信机器人 |
| `WECHAT_DOWNLOAD_DIR` | `downloads` | 接收图片的保存目录 |
| `WECHAT_QR_CODE_PATH` | `wechat-login-qr.png` | 登录二维码路径 |
| `RAG_ENABLED` | `true` | 是否启用本地关键词 RAG |
| `RAG_KNOWLEDGE_BASE` | `classpath:rag/knowledge-base.json` | 知识库位置 |
| `RAG_MAX_RESULTS` | `3` | 最多注入的知识片段数，范围 1～10 |
| `DAILY_BRIEF_DEFAULT_LOCATION` | `北京` | 每日简报默认城市 |
| `DAILY_BRIEF_DEFAULT_NEWS_TOPIC` | `人工智能` | 每日简报默认新闻主题 |
| `DAILY_BRIEF_NEWS_LIMIT` | `3` | 每日简报新闻条数，范围 1～10 |
| `NODE_EXECUTABLE` | `node` | Node.js 命令或绝对路径 |
| `SILK_DECODE_TIMEOUT_SECONDS` | `30` | SILK 解码超时时间 |

聊天、意图、搜索、翻译、视觉、生图、ASR、TTS 模型均可通过 `DASHSCOPE_*_MODEL` 环境变量替换。完整映射和接口地址见 [`application.yaml`](src/main/resources/application.yaml)。

## 目录结构

```text
src/main/java/com/example/demo
├── config/              # AI、天气、音频、RAG、Skill 配置
├── service/
│   ├── wechat/          # 登录、收发消息和媒体处理
│   ├── routing/         # Skill → RAG → LLM 总路由
│   ├── ai/              # 百炼模型与联网能力客户端
│   ├── audio/           # Java → Node.js 的 SILK 解码调用
│   └── weather/         # 心知天气客户端
├── tool/                # Tool 接口、注册表、调用引擎及内置工具
├── skill/               # Skill 接口、注册表和每日简报
├── rag/                 # 本地关键词检索与 Prompt 增强
└── model/               # 路由、意图和天气数据模型

src/main/resources/
├── application.yaml
└── rag/knowledge-base.json

scripts/                 # SILK 解码和自检脚本
src/test/                # 路由、RAG、Skill、Tool 单元测试
```

项目只通过微信 iLink SDK 收发消息，目前不提供 HTTP 接口。

## 如何扩展

### 新增 Tool

1. 在 `tool/` 中实现 `BotTool`。
2. 提供唯一名称、说明和 JSON Schema，并使用 `@Component` 注册。
3. 在 `execute(JsonNode)` 中校验外部参数并返回结构化结果。
4. 为成功、非法参数和第三方失败补充测试。

`ToolRegistry` 会自动发现 Tool，无需修改集中式 `if/else`。Tool 可能由虚拟线程并行执行，因此应保持无共享可变状态或自行保证线程安全。

适合继续增加：世界时间、汇率、日历、待办、快递、数据库查询和企业内部 API。

### 新增 Skill

实现 `BotSkill` 并添加 `@Component`，声明名称、关键词和执行流程即可自动注册。适合将多个 Tool 固化为稳定流程，例如早报、行程助手、客服工单或日报生成。

关键词冲突时优先匹配更长的关键词；重复关键词会在启动时直接报错。

### 扩展 RAG

当前知识库位于 [`knowledge-base.json`](src/main/resources/rag/knowledge-base.json)，每条记录包含：

```json
{
  "id": "unique-id",
  "title": "标题",
  "keywords": ["关键词1", "关键词2"],
  "content": "提供给模型的知识内容"
}
```

知识库在应用启动时加载，修改后需要重启。数据量增大后，可以进一步接入 Embedding、向量数据库、混合检索、文档切分、重排和来源引用。

### 推荐的工程化方向

- 增加按微信用户隔离的会话记忆、数据库持久化和上下文压缩。
- 抽象 AI Provider 与消息渠道，支持其他模型或 Web、企业微信等入口。
- 为第三方请求增加统一超时、重试、限流、熔断和监控指标。
- 为图片下载目录增加定期清理或改用对象存储。
- 增加权限控制、敏感词处理、操作审计和高风险 Tool 二次确认。
- 将关键词 RAG 升级为可增量更新的语义知识库。

## 当前边界

- RAG 是适合教学和小型知识库的关键词匹配，不理解同义词或复杂语义。
- 每次普通对话相互独立，目前没有长期记忆、用户数据隔离或数据库。
- 收到的图片会保存到 `downloads/`，不会自动清理。
- 入站语音在内存和进程管道中解码，不生成中间音频文件。
- 语音回复以 `qwen-answer.wav` 文件发送，不是微信语音气泡。
- 天气、新闻和各类模型能力依赖外部网络、API Key 与服务可用性。

## 测试

```bash
npm run audio:check
./mvnw clean test
```

单元测试覆盖 Tool 参数校验、并行/多轮 Function Calling、Skill 优先级、RAG 开关与消息路由。真实微信登录、第三方 API 和多模态结果仍建议在本地进行端到端验证。

## 安全与第三方组件

不要提交 API Key、登录二维码、本地配置或接收到的图片。仓库已忽略 `.env`、`application-local.yml`、`downloads/`、`wechat-login-qr.png` 和 `node_modules/`。

微信语音解码使用 MIT 许可的 [`silk-wasm`](https://github.com/idranme/silk-wasm)，详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
