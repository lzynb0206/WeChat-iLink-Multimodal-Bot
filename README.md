<div align="center">

# 🤖 WeChat iLink Multimodal Bot

**A practical, extensible foundation for building AI-powered WeChat assistants.**

Connect WeChat with Qwen models, multimodal interaction, tool calling, reusable skills, and local knowledge.

<p>
  <a href="./README.md">
    <img src="https://img.shields.io/badge/English-Default-2563EB?style=for-the-badge" alt="English README">
  </a>
  <a href="./README.zh-CN.md">
    <img src="https://img.shields.io/badge/简体中文-阅读文档-DC2626?style=for-the-badge" alt="简体中文 README">
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

> This project is more than a chat demo, but intentionally remains small enough to understand and extend. Use it as a starting point for personal assistants, knowledge bots, customer-service workflows, automation agents, or your own WeChat AI product.

## ✨ Highlights

| | Capability | What it provides |
| --- | --- | --- |
| 💬 | WeChat integration | QR-code login and text, image, voice, file, and image replies |
| 🧠 | Multimodal AI | Chat, intent recognition, image understanding, image generation, ASR, and TTS |
| 🛠️ | Function Calling | Validated tool schemas, multi-round execution, and structured results |
| ⚡ | Parallel tools | Independent calls run concurrently on Java 21 virtual threads |
| 🧩 | Reusable Skills | Deterministic workflows that combine multiple tools |
| 📚 | Local RAG | Lightweight keyword retrieval from a JSON knowledge base |
| 🌤️ | Live information | Weather, web-connected news, translation, and calculations |
| 🚦 | Layered routing | Predictable routing through **Skill → RAG → LLM/Tool** |

## 🧱 Built to Extend

The repository already contains the essential building blocks of an AI assistant:

- A real messaging channel instead of a mock HTTP endpoint.
- Multimodal input and output pipelines.
- A tool registry that automatically discovers new `BotTool` components.
- A skill registry for stable, reusable business workflows.
- A knowledge layer that can later be upgraded to vector search.
- Configuration, validation, error handling, and unit tests.

You can build on this foundation without rewriting the message lifecycle. Add one tool, one skill, or one knowledge source at a time as the product grows.

## 🔄 How It Works

```text
Text ───────────────────────────────┐
WeChat voice → SILK → WAV → ASR ────┼→ MessageRouter
                                    ├→ Skill match: run a deterministic workflow
                                    ├→ RAG match: add local knowledge to the prompt
                                    └→ LLM: classify intent, chat, or call tools

WeChat image → download → vision model → text reply
Image request → image model → image reply
Voice reply → TTS → WAV file reply
```

### Routing priority

1. **Skill** handles explicit, repeatable workflows.
2. **RAG** answers questions using project-specific knowledge.
3. **LLM/Tool** handles open-ended chat, image requests, and tool selection.

This keeps known workflows fast and predictable while preserving the flexibility of an LLM.

## 🧰 Built-in Capabilities

### Tools

| Tool | Purpose | Backing service |
| --- | --- | --- |
| `get_current_weather` | Current weather for a city or district | Seniverse Weather |
| `search_news` | Recent news with sources and links | DashScope web search |
| `translate_text` | Multilingual text translation | Qwen MT |
| `calculate` | Precise decimal arithmetic | Java `BigDecimal` |
| `convert_temperature` | Celsius, Fahrenheit, and Kelvin conversion | Local Java code |

The tool engine supports both patterns:

- **Parallel:** query multiple cities and calculate a value in the same round.
- **Sequential:** query the current temperature first, then convert the returned value.

### Skill

The included `daily_brief` skill runs weather and news tools concurrently and combines their results into a consistent daily brief.

```text
Generate a daily brief 城市=杭州，主题=大模型
```

### Local RAG

The included RAG implementation scores keyword matches against a local JSON knowledge base, selects the top results, and injects them into the model prompt. It is intentionally lightweight and suitable for learning, prototypes, and small curated datasets.

## 🧑‍💻 Tech Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java 21, Node.js 18+ |
| Framework | Spring Boot 4.1.0, Spring Web |
| WeChat | `wechat-ilink-sdk` 2.3.3, ZXing |
| AI | Alibaba Cloud DashScope compatible and native APIs |
| Models | Qwen Chat/VL/Image/ASR/MT, CosyVoice TTS |
| Live data | Seniverse Weather, DashScope web search |
| Audio | `silk-wasm` 3.7.1, process pipes, WAV |
| Engineering | Maven, Jackson, Lombok, JUnit 5 |
| Concurrency | Java 21 virtual threads |

## 🚀 Quick Start

### 1. Requirements

```bash
java -version   # Java 21+
node --version  # Node.js 18+
npm --version
```

Install the SILK audio dependency and verify the codec pipeline:

```bash
npm ci
npm run audio:check
```

### 2. Configure API keys

```bash
export DASHSCOPE_API_KEY="your DashScope API key"
export SENIVERSE_API_KEY="your Seniverse private key"
```

`DASHSCOPE_API_KEY` is required for AI features. `SENIVERSE_API_KEY` is only required for weather and the daily brief. Use the Seniverse private `key`, not the public `uid`.

Alternatively, create the Git-ignored file `src/main/resources/application-local.yml`:

```yaml
dashscope:
  api-key: "sk-..."

weather:
  api-key: "..."
```

### 3. Test and run

```bash
./mvnw test
./mvnw spring-boot:run
```

The application creates `wechat-login-qr.png` in the project root. Scan it with WeChat, then send a message to the bot.

To start the Spring context without logging in to WeChat:

```bash
WECHAT_BOT_ENABLED=false ./mvnw spring-boot:run
```

## 💬 Try These Messages

```text
What can you do?
查询上海天气，并把温度换算成华氏度
查询今天的人工智能新闻，返回 3 条并附来源
把“你好，世界”翻译成英文
生成每日简报 城市=杭州，主题=大模型
RAG 是什么，它在这个项目中怎么实现？
生成一张雨中的西湖
用语音介绍一下杭州
```

## ⚙️ Common Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `WECHAT_BOT_ENABLED` | `true` | Enable the WeChat bot |
| `WECHAT_DOWNLOAD_DIR` | `downloads` | Directory for received images |
| `WECHAT_QR_CODE_PATH` | `wechat-login-qr.png` | Login QR-code path |
| `RAG_ENABLED` | `true` | Enable local keyword RAG |
| `RAG_KNOWLEDGE_BASE` | `classpath:rag/knowledge-base.json` | Knowledge-base location |
| `RAG_MAX_RESULTS` | `3` | Maximum retrieved documents, from 1 to 10 |
| `DAILY_BRIEF_DEFAULT_LOCATION` | `北京` | Default brief location |
| `DAILY_BRIEF_DEFAULT_NEWS_TOPIC` | `人工智能` | Default brief topic |
| `DAILY_BRIEF_NEWS_LIMIT` | `3` | Number of news items, from 1 to 10 |
| `NODE_EXECUTABLE` | `node` | Node.js command or absolute path |
| `SILK_DECODE_TIMEOUT_SECONDS` | `30` | SILK decode timeout |

All model names can be overridden with the corresponding `DASHSCOPE_*_MODEL` variables. See [`application.yaml`](src/main/resources/application.yaml) for the complete configuration.

## 📁 Project Structure

```text
src/main/java/com/example/demo
├── config/              # AI, weather, audio, RAG, and skill settings
├── service/
│   ├── wechat/          # Login, messages, and media handling
│   ├── routing/         # Skill → RAG → LLM routing
│   ├── ai/              # DashScope model clients
│   ├── audio/           # Java → Node.js SILK decoding
│   └── weather/         # Seniverse client
├── tool/                # Tool API, registry, engine, and built-in tools
├── skill/               # Skill API, registry, and daily brief
├── rag/                 # Keyword retrieval and prompt augmentation
└── model/               # Intent, route, and weather models

src/main/resources/
├── application.yaml
└── rag/knowledge-base.json

scripts/                 # SILK decoder and self-test
src/test/                # Tool, Skill, RAG, and routing tests
```

The application communicates through the WeChat iLink SDK and does not currently expose an HTTP API.

## 🧩 Extension Guide

### Add a Tool

1. Implement `BotTool` under `tool/`.
2. Provide a unique name, description, and JSON Schema.
3. Add `@Component`; `ToolRegistry` discovers it automatically.
4. Validate all external parameters and return structured output.
5. Add tests for success, invalid input, and upstream failures.

Tools may run concurrently, so implementations should be stateless or thread-safe.

### Add a Skill

Implement `BotSkill`, add `@Component`, and declare its name, keywords, and workflow. Skills are ideal for morning briefs, trip planning, support tickets, reports, or other repeatable multi-tool tasks.

### Extend the Knowledge Base

Edit [`knowledge-base.json`](src/main/resources/rag/knowledge-base.json):

```json
{
  "id": "unique-id",
  "title": "Document title",
  "keywords": ["keyword-1", "keyword-2"],
  "content": "Knowledge supplied to the model"
}
```

The knowledge base is loaded at startup, so restart the application after editing it.

## 🌱 What You Can Build Next

- 👤 A personal assistant with per-user memory and scheduled reminders.
- 🎧 A customer-service bot with private product knowledge and ticket workflows.
- 🏢 An internal knowledge assistant connected to company documents and APIs.
- 📅 A productivity bot with calendars, todos, email, weather, and daily briefs.
- 🔎 A semantic RAG system with embeddings, vector search, reranking, and citations.
- 🌐 A multi-channel assistant by abstracting the AI provider and messaging adapter.
- 🛡️ A production service with authorization, audit logs, rate limits, retries, and observability.

## ⚠️ Current Scope

- RAG uses keyword matching rather than embeddings or semantic retrieval.
- Conversations are stateless; there is no persistent memory or database.
- Received images are stored under `downloads/` and are not removed automatically.
- Incoming voice is decoded in memory; voice replies are sent as WAV files, not WeChat voice bubbles.
- AI, weather, and news features depend on external networks, credentials, and service availability.

## ✅ Testing

```bash
npm run audio:check
./mvnw clean test
```

Tests cover validation, parallel and multi-round tool calling, skill priority, the RAG switch, and message routing. Real WeChat login and external AI services should also be verified end to end.

## 🔐 Security and Third-Party Software

Do not commit API keys, login QR codes, local configuration, or downloaded user images. The repository ignores `.env`, `application-local.yml`, `downloads/`, `wechat-login-qr.png`, and `node_modules/`.

WeChat voice decoding uses the MIT-licensed [`silk-wasm`](https://github.com/idranme/silk-wasm). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
