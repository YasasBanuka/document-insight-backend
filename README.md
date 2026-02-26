<div align="center">

<h1>🧠 Docura — Backend</h1>
<h3><em>Intelligence for Your Documents</em></h3>
<p>Spring Boot 3 · Java 21 · pgvector · Spring AI</p>

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.5-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16_pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

*The backend powering Docura — "Intelligence for Your Documents". A multi-tenant RAG engine that transforms your uploaded documents into a semantically queryable, AI-ready knowledge base.*

**[📡 API Reference](docs/API.md) · [🏗️ Architecture](docs/architecture.md) · [🔐 Security & Testing](docs/security_and_testing.md) · [🚀 Deployment Repo](https://github.com/YasasBanuka/docura-deployment)**

</div>

---

## ✨ What This Service Does

This Spring Boot application is the core intelligence of Docura. It:

1. **Ingests** uploaded PDFs, DOCX, and TXT files using Apache PDFBox and Apache POI.
2. **Chunks** the extracted text using a sliding-window algorithm (2000 chars, 200-char overlap, sentence-boundary detection).
3. **Embeds** each chunk locally on the JVM using the ONNX `all-MiniLM-L6-v2` model (384-dimensional vectors) — no third-party embedding API costs.
4. **Stores** vectors in PostgreSQL 16 with the `pgvector` extension.
5. **Retrieves** the top 7 most relevant chunks per user query using the `<=>` cosine distance operator, filtered by the authenticated user's ID.
6. **Generates** a contextual answer by injecting the retrieved chunks into a LLaMA prompt, streamed back via Server-Sent Events (SSE).

---

## 🏗️ Project Structure

```
src/main/java/com/webdynamo/document_insight/
├── config/
│   ├── SecurityConfig.java          # Spring Security: JWT filter chain, CORS, BCrypt
│   ├── JwtAuthenticationFilter.java # Extracts + validates JWT on every request
│   ├── RateLimitFilter.java         # Bucket4j: 100/min auth, 10/min unauth
│   ├── RAGRateLimitFilter.java      # Bucket4j: 20/min for /conversations/* endpoints
│   ├── FileStorageConfig.java       # Upload directory initialization
│   └── OpenApiConfig.java           # Swagger/OpenAPI configuration (disabled in prod)
│
├── controller/
│   ├── AuthController.java          # /api/auth: register, login, refresh
│   ├── DocumentController.java      # /api/documents: upload, search, chat, conversations
│   └── UserController.java          # /api/user: profile get/update
│
├── service/
│   ├── DocumentService.java         # Upload pipeline orchestration (@Transactional)
│   ├── DocumentParserService.java   # PDF/DOCX/TXT → raw text
│   ├── TextChunkingService.java     # Sliding window chunking (2000/200 chars)
│   ├── EmbeddingService.java        # ONNX MiniLM-L6-v2 → float[384]
│   ├── VectorSearchService.java     # JdbcTemplate cosine-similarity SQL queries
│   ├── RAGQueryService.java         # Retrieve + Augment + Generate pipeline
│   ├── ConversationService.java     # Conversation CRUD + @EntityGraph loading
│   ├── AuthenticationService.java   # Registration, login, token refresh
│   ├── JwtService.java              # HS256 token generation + validation
│   ├── RateLimitService.java        # Bucket management (per user ID / per IP)
│   └── MetricsService.java          # Micrometer custom metric recording
│
├── model/                           # JPA Entities: User, Document, DocumentChunk,
│                                    #   Conversation, ConversationMessage, Role
├── repo/                            # Spring Data JPA repos + @EntityGraph queries
├── dto/                             # Request/Response DTOs + AuthResponse records
└── exception/                       # GlobalExceptionHandler, DocumentNotFoundException
```

---

## 🛠️ Technology Highlights

| Concern | Solution |
|---|---|
| **Embeddings** | ONNX `all-MiniLM-L6-v2` on JVM — zero API cost, zero data egress |
| **Text Generation** | Groq `llama-3.3-70b-versatile` via Spring AI (prod) / Ollama (local) |
| **Vector Search** | PostgreSQL pgvector `<=>` cosine distance, user-filtered SQL |
| **Authentication** | Stateless JWT HS256, BCrypt password hashing |
| **Rate Limiting** | Bucket4j token-bucket: 3 separate tiers |
| **Lazy Load Safety** | `@EntityGraph(attributePaths={"messages"})`, `open-in-view: false` |
| **Observability** | Micrometer → `/actuator/prometheus` (scraped by Prometheus every 15s) |

---

## ⚙️ Local Development Setup

### Prerequisites
- Java 21 JDK
- Maven 3.9+
- PostgreSQL 16 with `pgvector` extension (or Docker)
- [Ollama](https://ollama.ai) running locally with `llama3.2` and `all-minilm` models

### 1. Start PostgreSQL with pgvector

```bash
docker run -d \
  --name docura-postgres \
  -e POSTGRES_DB=docura \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5433:5432 \
  pgvector/pgvector:pg16

# Enable pgvector extension
docker exec -it docura-postgres psql -U postgres -d docura \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 2. Configure Environment

The application uses `${VAR:default}` syntax — for local development, the defaults in `application.yaml` work out-of-the-box. Optionally create a `.env` file:

```bash
cp .env.example .env
# Edit .env with your values
```

### 3. Run the Application

```bash
# Using the local profile (Ollama-based AI)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# The API is available at http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html (disabled in prod profile)
```

### 4. Run Tests

```bash
# All tests
./mvnw test

# Specific test class
./mvnw test -Dtest=ConversationServiceTest
./mvnw test -Dtest=DocumentControllerTest
./mvnw test -Dtest=TextChunkingServiceTest

# With test coverage report
./mvnw verify
```

---

## 🔐 Security Model

All protected endpoints require `Authorization: Bearer <access_token>`.

The security filter chain executes in this exact order:
```
CorsFilter → JwtAuthenticationFilter → RateLimitFilter → RAGRateLimitFilter → AuthorizationFilter
```

Cross-tenant data isolation is enforced at two independent layers:
1. **Service layer**: `findByIdAndUserId(id, userId)` — throws if wrong owner.
2. **SQL layer**: `WHERE d.user_id = ?` in all vector search queries.

See [Security & Testing Report](docs/security_and_testing.md) for the full breakdown.

---

## 📡 Spring Profiles

| Profile | Chat Model | Embedding Model |
|---|---|---|
| `local` | Ollama `llama3.2` | Ollama `all-minilm` |
| `prod` | Groq `llama-3.3-70b-versatile` | ONNX `all-MiniLM-L6-v2` (JVM) |

Activate with: `-Dspring-boot.run.profiles=prod` or `SPRING_PROFILES_ACTIVE=prod` env var.

---

## 🐳 Docker

```bash
# Build multi-stage image for linux/amd64 (required for AWS EC2)
docker buildx build \
  --platform linux/amd64 \
  -t ybanuka/docura-backend:latest \
  --push .
```

**Base image:** Ubuntu 22.04 Jammy (not Alpine) — required because the ONNX DJL native library needs `glibc`, which Alpine Linux does not provide.

---

## 📚 Further Documentation

| Document | Description |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System design, RAG pipeline diagrams, ERD, design decisions |
| [docs/API.md](docs/API.md) | Complete REST API reference with request/response examples |
| [docs/security_and_testing.md](docs/security_and_testing.md) | JWT filter chain, rate limiting, test case reference |
| [docs/deployment.md](docs/deployment.md) | AWS EC2 setup, Docker Compose, Prometheus/Grafana config |

---

## 🔗 Related Repositories

- **[docura-frontend](https://github.com/YasasBanuka/document-insight-frontend)** — React 18 + TypeScript SPA
- **[docura-deployment](https://github.com/YasasBanuka/docura-deployment)** — Docker Compose, Nginx, Prometheus config for AWS deployment

---

<div align="center">
  <sub>Built by <strong>Yasas Banuka</strong> · Spring Boot 3 · pgvector · Groq LLaMA · ONNX Embeddings</sub>
</div>
