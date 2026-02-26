# 📗 Docura: Complete REST API Reference

> **Base URL (Production):** `http://<your-ec2-ip>/api`  
> **Base URL (Local):** `http://localhost:8080/api`  
> **Content-Type:** `application/json` (unless noted)  
> **Authentication:** All endpoints except those marked *Public* require `Authorization: Bearer <access_token>`

---

## Table of Contents
1. [Authentication](#1-authentication-apiauth)
2. [User Profile](#2-user-profile-apiuser)  
3. [Document Management](#3-document-management-apidocuments)
4. [RAG Chat](#4-rag-chat-apidocumentsconversations)
5. [Search](#5-search-apidocumentssearch)
6. [Rate Limiting](#6-rate-limiting)
7. [Error Reference](#7-error-reference)

---

## 1. Authentication (`/api/auth`)

These endpoints are **public** (no JWT required). They are rate-limited to **10 requests/min per IP**.

### `POST /api/auth/register`

Creates a new user account. The password is automatically hashed with BCrypt (cost factor 10) before storage.

**Request Body:**
```json
{
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "password": "SecurePassword123"
}
```

**Response `200 OK`:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
  "user_id": 15,
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "role": "USER",
  "token_type": "Bearer"
}
```

**Error Responses:**
| Status | Condition |
|---|---|
| `400 Bad Request` | Email already registered |
| `400 Bad Request` | Validation failure (invalid email format, empty password) |

---

### `POST /api/auth/login`

Authenticates an existing user. Spring Security's `AuthenticationManager` validates the BCrypt hash internally.

**Request Body:**
```json
{
  "email": "jane.doe@example.com",
  "password": "SecurePassword123"
}
```

**Response `200 OK`:** Same shape as `/register`.

**Error Responses:**
| Status | Condition |
|---|---|
| `401 Unauthorized` | Invalid credentials |
| `429 Too Many Requests` | Rate limit exceeded (10/min per IP) |

---

### `POST /api/auth/refresh`

Exchanges an expired access token for a new token pair. The refresh token has a **7-day** validity window.

**Request Body:**
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response `200 OK`:** Same shape as `/register` with fresh tokens.

**Error Responses:**
| Status | Condition |
|---|---|
| `400 Bad Request` | Invalid or expired refresh token |

---

## 2. User Profile (`/api/user`)

### `GET /api/user/me`

Returns the currently authenticated user's profile. The `@AuthenticationPrincipal` extracts the user identity from the JWT.

**Response `200 OK`:**
```json
{
  "id": 15,
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "role": "USER",
  "createdAt": "2026-02-01T10:30:00"
}
```

---

### `PUT /api/user/profile`

Updates the user's display name and/or email. Only the authenticated user can update their own profile.

**Request Body:**
```json
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com"
}
```

**Response `200 OK`:** Returns the updated `User` entity.

**Error Responses:**
| Status | Condition |
|---|---|
| `400 Bad Request` | New email is already in use by another account |

---

## 3. Document Management (`/api/documents`)

All endpoints in this group require authentication. Documents are strictly scoped to the authenticated user.

### `POST /api/documents/upload`

Uploads a document and runs the full ETL pipeline: parse → chunk → embed → store in pgvector.

- **Content-Type:** `multipart/form-data`
- **Rate Limit:** 100 req/min (authenticated)
- **File size limit:** 10 MB (enforced at both Spring multipart filter and service layer)
- **Supported types:** `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`

**Request:** Form field `file` (binary)

**Response `200 OK`:**
```json
{
  "id": 42,
  "filename": "quarterly_report_q4.pdf",
  "message": "File uploaded and processed successfully. 28 chunks created.",
  "fileSize": 2097152,
  "contentType": "application/pdf"
}
```

**Error Responses:**
| Status | Condition |
|---|---|
| `400 Bad Request` | File is empty |
| `400 Bad Request` | Unsupported file type |
| `400 Bad Request` | File exceeds 10MB limit |
| `429 Too Many Requests` | Rate limit exceeded |

---

### `GET /api/documents`

Returns all documents belonging to the authenticated user, sorted by creation date descending.

**Response `200 OK`:**
```json
[
  {
    "id": 42,
    "filename": "quarterly_report_q4.pdf",
    "contentType": "application/pdf",
    "fileSize": 2097152,
    "createdAt": "2026-02-20T14:22:11",
    "chunkCount": 28
  }
]
```

---

### `GET /api/documents/{id}`

Retrieves metadata for a specific document. Returns 404 if the document does not exist or belongs to a different user (ownership enforced).

**Response `200 OK`:** Same shape as one element of `GET /api/documents`.

**Error Responses:**
| Status | Condition |
|---|---|
| `404 Not Found` | Document not found or not owned by user |

---

### `DELETE /api/documents/{id}`

Deletes a document including:
1. All `document_chunk` rows (and their vectors) for that document.
2. The physical file from the server's upload directory.
3. The `documents` table row.

This is a `@Transactional` operation — all or nothing.

**Response `204 No Content`**

---

### `GET /api/documents/{id}/download`

Downloads the original binary file that was uploaded. Response header `Content-Disposition: attachment; filename="original.pdf"` is set via `Content-Disposition` exposure in CORS config.

**Response:** Binary stream, `Content-Type` matching original upload.

---

### `GET /api/documents/{id}/preview`

Returns the plain text extracted from the document (i.e., the result of Apache PDFBox/POI parsing, before chunking).

**Response `200 OK`:** `text/plain` — the full raw extracted text.

---

## 4. RAG Chat (`/api/documents/conversations`)

Conversations are the core RAG feature. A `Conversation` is a thread of `Question → Answer` pairs, each grounded by document context.

### `POST /api/documents/conversations`

Creates a new conversation thread, runs the full RAG pipeline for the first message, and returns the created conversation.

> **Rate Limit:** 20 req/min (authenticated), 5 req/min (unauthenticated)

**Request Body:**
```json
{
  "question": "What is the net profit margin described in the report?"
}
```

**Response `200 OK`:**
```json
{
  "id": 9,
  "title": "What is the net profit margin desc...",
  "createdAt": "2026-02-27T20:43:37",
  "updatedAt": "2026-02-27T20:43:37",
  "messages": [
    {
      "id": 17,
      "type": "QUESTION",
      "content": "What is the net profit margin described in the report?",
      "sources": null,
      "createdAt": "2026-02-27T20:43:37"
    },
    {
      "id": 18,
      "type": "ANSWER",
      "content": "Based on the uploaded documents, the net profit margin for Q4 was reported at 18.3%...",
      "sources": [
        {
          "documentId": 42,
          "filename": "quarterly_report_q4.pdf",
          "chunkIndex": 4,
          "similarity": 0.89
        }
      ],
      "createdAt": "2026-02-27T20:43:37"
    }
  ]
}
```

---

### `POST /api/documents/conversations/{id}/messages`

Appends a new question/answer pair to an existing conversation. Runs the full RAG pipeline and returns the updated conversation.

> **Rate Limit:** 20 req/min (authenticated)

**Request Body:** Same as `POST /api/documents/conversations`.
**Response `200 OK`:** The complete updated `ConversationDTO` with all messages.

**Error Responses:**
| Status | Condition |
|---|---|
| `404 Not Found` | Conversation not found or not owned by user |
| `429 Too Many Requests` | RAG rate limit exceeded |

---

### `GET /api/documents/conversations`

Returns all conversations for the authenticated user, ordered by `updated_at` descending (most recent first).

Messages are eagerly loaded via `@EntityGraph(attributePaths = {"messages"})` to prevent `LazyInitializationException`.

**Response `200 OK`:** Array of `ConversationDTO` (same shape as above).

---

### `GET /api/documents/conversations/{id}`

Returns a single conversation by ID with full message history.

**Error Responses:**
| Status | Condition |
|---|---|
| `404 Not Found` | Conversation not found or not owned by user |

---

### `DELETE /api/documents/conversations/{id}`

Permanently deletes a conversation and all its messages (cascaded via `CascadeType.ALL`).

**Response `204 No Content`**

---

## 5. Search (`/api/documents/search`)

### `GET /api/documents/search`

Performs a paginated vector similarity search across the authenticated user's documents. Uses the `<=>` cosine distance operator in PostgreSQL pgvector.

**Query Parameters:**

| Parameter | Type | Required | Description | Default |
|---|---|---|---|---|
| `query` | `string` | ✅ | The natural language search query | — |
| `page` | `int` | No | Page number (0-based) | `0` |
| `size` | `int` | No | Results per page | `10` |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": 105,
      "documentId": 42,
      "chunkIndex": 4,
      "content": "The net profit margin for Q4 was 18.3%, an improvement of 2.1 percentage points...",
      "tokenCount": 87,
      "filename": "quarterly_report_q4.pdf",
      "similarity": 0.89
    }
  ],
  "currentPage": 0,
  "pageSize": 10,
  "totalElements": 28,
  "totalPages": 3
}
```

---

## 6. Rate Limiting

When a rate limit is exceeded, the API returns:

**Response `429 Too Many Requests`:**
```json
{
  "timestamp": "2026-02-27T20:00:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please retry after 60 seconds.",
  "path": "/api/documents/conversations",
  "retryAfter": 60
}
```

The `Retry-After` HTTP header (RFC 6585 compliant) is also set on the response.

**Bucket Summary:**

| Bucket | Endpoint Pattern | Auth | Limit |
|---|---|---|---|
| `auth_bucket` | `/api/**` (all) | Authenticated | 100 req / 1 min |
| `ip_bucket` | `/api/**` (all) | Unauthenticated | 10 req / 1 min |
| `rag_authenticated` | `/api/documents/conversations/**` | Authenticated | 20 req / 1 min |
| `rag_unauthenticated` | `/api/documents/conversations/**` | None | 5 req / 1 min |

---

## 7. Error Reference

All errors are returned as structured JSON. The `GlobalExceptionHandler` (`@RestControllerAdvice`) handles all uncaught exceptions.

| HTTP Status | When It Occurs |
|---|---|
| `400 Bad Request` | Validation failure, unsupported file type, business rule violation |
| `401 Unauthorized` | Missing, invalid, or expired JWT access token |
| `403 Forbidden` | Valid JWT but user does not own the resource |
| `404 Not Found` | `DocumentNotFoundException` or `RuntimeException("Conversation not found")` |
| `429 Too Many Requests` | Bucket4j rate limit bucket exhausted |
| `500 Internal Server Error` | Unhandled exception (logged with ERROR level) |

**Error Response Shape:**
```json
{
  "timestamp": "2026-02-27T20:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Document not found with id: 99",
  "path": "/api/documents/99"
}
```
