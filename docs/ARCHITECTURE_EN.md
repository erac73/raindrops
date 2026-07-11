# Rain Drops Architecture

> Technical architecture document for the threshold cryptography-based distributed storage model.

**Version:** 0.1.0 | **Phase:** 3 — Witness Node | **Author:** Edwar Antonio Ramírez Castillo

---

## Table of Contents

1. [Overview](#1-overview)
2. [System Modules](#2-system-modules)
3. [Cryptographic Architecture](#3-cryptographic-architecture)
4. [Data Flow](#4-data-flow)
5. [Network Architecture](#5-network-architecture)
6. [Security Model](#6-security-model)
7. [Deployment](#7-deployment)
8. [Configuration Reference](#8-configuration-reference)

---

## 1. Overview

Rain Drops implements a distributed storage model where confidentiality is a structural property of the model itself, not an added layer. The system fragments data using **Shamir's Secret Sharing (SSS)** into cryptographic units called *drops* that are individually indistinguishable from random noise.

### 1.1 Design Principles

| Principle | Description |
|---|---|
| **Perfect Secrecy** | With K-1 drops, the secret carries zero information — information-theoretic guarantee, not computational |
| **No Central Trust** | No single node possesses the complete data or the master key |
| **Independent Verification** | Each drop carries its own MAC; any party can verify integrity |
| **Temporal Expiry** | Drops expire; data access has inherent expiration |
| **Composability** | Modules (core, storage, witness) are independent and deployable separately |

### 1.2 Conceptual Metaphor

> *A raindrop carries no information about the storm.*

Just as a single raindrop reveals nothing about a storm's structure, a single cryptographic drop reveals nothing about the original data. Only when K of N drops converge — under integrity verification — does the data emerge.

---

## 2. System Modules

### 2.1 Module Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      Rain Drops System                          │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    raindrops-core                          │  │
│  │  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │  │ShamirSSS │  │ HybridScheme │  │  RainDropsCore   │    │  │
│  │  │ GF(p)    │  │ AES-256-GCM  │  │  drop/reconstruct│    │  │
│  │  └────┬─────┘  └──────┬───────┘  └────────┬─────────┘    │  │
│  │       │               │                    │              │  │
│  │  ┌────┴───────────────┴────────────────────┴─────────┐    │  │
│  │  │               Drop / DropFactory / DropSerializer   │    │  │
│  │  └────────────────────────────────────────────────────┘    │  │
│  │  ┌────────────────┐  ┌──────────────┐                     │  │
│  │  │   RainMap      │  │  RainClient  │                     │  │
│  │  │ (encrypted idx)│  │  (HTTP SDK)  │                     │  │
│  │  └────────────────┘  └──────────────┘                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │       raindrops-storage      │  │     raindrops-witness    │ │
│  │  ┌────────────────────────┐  │  │  ┌────────────────────┐ │ │
│  │  │ DropController         │  │  │  │WitnessController  │ │ │
│  │  │ POST/GET /drops        │  │  │  │ POST /witness/*   │ │ │
│  │  │ POST/GET/PUT /rainmaps │  │  │  └────────┬───────────┘ │ │
│  │  └───────────┬────────────┘  │  │           │              │ │
│  │  ┌───────────┴────────────┐  │  │  ┌────────┴───────────┐ │ │
│  │  │ DropService            │  │  │  │ WitnessService     │ │ │
│  │  │ RainMapService         │  │  │  │ (orchestrator)     │ │ │
│  │  │ ReplicationService     │  │  │  └────────────────────┘ │ │
│  │  └────────────────────────┘  │  └──────────────────────────┘ │
│  └──────────────────────────────┘                               │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 raindrops-core

**Purpose:** Core cryptographic library. No Spring Boot dependency; standalone JAR.

| Class | Responsibility |
|---|---|
| `ShamirSSS` | Shamir's Secret Sharing implementation over GF(2⁵²¹ − 1) |
| `HybridScheme` | AES-256-GCM encryption for hybrid mode |
| `Drop` | Drop data model: `(id, x, y, mac, ttl)` |
| `DropFactory` | Drop creation and verification with HMAC-SHA256 |
| `DropSerializer` | JSON serialization/deserialization of drops |
| `RainDropsCore` | Main facade: `drop()` and `reconstruct()` operations |
| `RainMap` | Encrypted index mapping drop IDs to node URLs |
| `RainClient` | HTTP client SDK for operations against remote nodes |

**Dependencies:** Bouncy Castle (`bcprov-jdk18on`), Jackson (`jackson-databind`)

### 2.3 raindrops-storage

**Purpose:** Spring Boot storage node that persists drops and RainMaps.

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Database | Embedded H2 (file-based) |
| ORM | Spring Data JPA / Hibernate |
| API | REST (Spring Web) |
| API Docs | Springdoc OpenAPI / Swagger UI |
| Metrics | Prometheus + Micrometer |
| Templating | Thymeleaf (Dashboard) |
| Security | Spring Security + API Key filter |

**REST Endpoints:**

| Method | Route | Description |
|---|---|---|
| POST | `/drops` | Store a drop |
| GET | `/drops/{dropId}` | Retrieve a drop |
| POST | `/rainmaps` | Build and store RainMap |
| POST | `/rainmaps/external` | Store replicated RainMap |
| GET | `/rainmaps/{rainMapId}` | Retrieve RainMap |
| PUT | `/rainmaps/{rainMapId}/ciphertext` | Update ciphertext |
| GET | `/peers` | List known peers |
| GET | `/health` | Health check |

**Replication:** Asynchronous (fire-and-forget) to all configured peers via `PEER_URLS`.

### 2.4 raindrops-witness

**Purpose:** Stateless witness node that orchestrates and verifies operations.

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Communication | `RestClient` (Spring 6) |
| API | REST (Spring Web) |

**REST Endpoints:**

| Method | Route | Description |
|---|---|---|
| POST | `/witness/store` | Fragment, distribute, and store |
| POST | `/witness/reconstruct` | Collect, verify, and reconstruct |
| POST | `/witness/verify` | Verify a single drop's integrity |
| GET | `/health` | Health check |

---

## 3. Cryptographic Architecture

### 3.1 Shamir's Secret Sharing (SSS)

**Parameters:**
- **Prime:** `p = 2⁵²¹ − 1` (Mersenne prime, 521 bits)
- **Finite field:** GF(p)
- **Max secret:** 65 bytes (`⌊521/8⌋`)

**Split Algorithm (`split`):**
1. Generate random polynomial of degree `k−1`: `f(x) = s + a₁x + a₂x² + ... + aₖ₋₁xᵏ⁻¹`
   - `s` = secret (BigInteger < p)
   - `aᵢ` = uniform random coefficients in GF(p)
2. Evaluate `f(x)` at `x = 1, 2, ..., n` using **Horner's method** (O(k) per evaluation)
3. Return `n` pairs `(x, f(x))` — the *shares*

**Combine Algorithm (`combine`):**
1. Take `k` shares `(xᵢ, yᵢ)`
2. Lagrange interpolation: `s = Σᵢ yᵢ · Lᵢ(0) mod p`
   - `Lᵢ(0) = Πⱼ≠ᵢ (−xⱼ) · (xᵢ − xⱼ)⁻¹ mod p`
3. Return `s`

**Security:** With fewer than `k` shares, the distribution of `s` is uniform over GF(p) — perfect secrecy (Proof: Theorem 1 of Shamir, 1979).

### 3.2 Hybrid Scheme (HybridScheme)

**Direct Mode** (data ≤ 65 bytes):
- SSS secret is the data itself (padded to 65 bytes)
- No ciphertext generated
- `directMode = true`

**Hybrid Mode** (data > 65 bytes):
1. Generate random AES-256 key (32 bytes)
2. Encrypt data with AES-256-GCM → `nonce(12) || ciphertext || tag(16)`
3. SSS secret is the AES key (32 bytes)
4. Ciphertext stored separately
5. `directMode = false`

**AES-256-GCM Encryption:**
- Implementation: Bouncy Castle `GCMBlockCipher(AESEngine)`
- Nonce: 12 bytes (NIST recommendation for GCM)
- Tag: 128 bits
- Security: IND-CCA2

### 3.3 Drop

**Structure:** `d = (id, x, y, mac, ttl)`

| Field | Size | Description |
|---|---|---|
| `id` | 32 bytes | `HMAC-SHA256(nonce, masterKey)` — opaque, unpredictable ID |
| `x` | 4 bytes | SSS coordinate (1-based index) |
| `y` | ~66 bytes | Share value: `f(x) mod p` (BigInteger) |
| `mac` | 32 bytes | `HMAC-SHA256(x ‖ y ‖ ttl, masterKey)` |
| `ttl` | 8 bytes | Unix expiration timestamp |

**Security Properties:**
- An isolated drop is indistinguishable from random noise
- The ID reveals no relationship to other drops in the same set
- The MAC binds the drop to its master key and TTL
- Expired drops are irrecoverable

### 3.4 RainMap

**Purpose:** Encrypted index mapping `dropID → storageNodeURL`.

**Lifecycle:**
1. **Creation:** `RainMap.create(drops, urls, masterKey)` serializes the index as JSON, encrypts with AES-256-GCM using `masterKey`, stores combined nonce + ciphertext
2. **Storage:** Combined payload sent to the first storage node as `encryptedPayloadHex`
3. **Retrieval:** `RainMap.fromEncrypted(payload, masterKey)` decrypts and reconstructs the index
4. **Usage:** `rainMap.unseal(masterKey)` returns `Map<dropId, nodeUrl>` to locate drops

**Encrypted Payload Format:**
```
[nonce (12 bytes)] [AES-GCM ciphertext]
```
Where `ciphertext` decrypts to:
```json
{
  "urlIndex": { "abc123...": "http://keeper-1:8080", ... },
  "n": 5,
  "k": 3
}
```

---

## 4. Data Flow

### 4.1 Store (STORE)

```
Client                    Witness                         Storage Nodes
  │                          │                                  │
  │  POST /witness/store     │                                  │
  │  {data, n, k, ttlDays}   │                                  │
  │ ──────────────────────►  │                                  │
  │                          │  RainDropsCore.drop(data,n,k)    │
  │                          │  ──► N Drops + masterKey         │
  │                          │       + ciphertext (optional)    │
  │                          │                                  │
  │                          │  POST /drops (drop[0]) ────────► │ keeper-1
  │                          │  POST /drops (drop[1]) ────────► │ keeper-2
  │                          │  POST /drops (drop[2]) ────────► │ keeper-3
  │                          │  POST /drops (drop[3]) ────────► │ keeper-1
  │                          │  POST /drops (drop[4]) ────────► │ keeper-2
  │                          │                                  │
  │                          │  RainMap.create(drops,urls,mk)  │
  │                          │  ──► encryptedPayloadHex         │
  │                          │                                  │
  │                          │  POST /rainmaps/external ───────►│ keeper-1
  │                          │  {rainMapId, encryptedPayload,   │
  │                          │   n, k}                          │
  │                          │                                  │
  │                          │  PUT /rainmaps/{id}/ciphertext ─►│ keeper-1
  │                          │  (hybrid mode only)              │
  │                          │                                  │
  │  {rainMapId, masterKey}  │                                  │
  │ ◄──────────────────────  │                                  │
```

**Round-Robin Distribution:** N drops are distributed across storage nodes cyclically. For N=5 and 3 nodes: drop[0]→keeper-1, drop[1]→keeper-2, drop[2]→keeper-3, drop[3]→keeper-1, drop[4]→keeper-2.

### 4.2 Reconstruct (RECONSTRUCT)

```
Client                    Witness                         Storage Nodes
  │                          │                                  │
  │  POST /witness/reconstruct                                  │
  │  {rainMapId, masterKeyHex}                                  │
  │ ──────────────────────►  │                                  │
  │                          │  GET /rainmaps/{id} ───────────► │ keeper-1
  │                          │ ◄─────────────────────────────── │
  │                          │  {encryptedPayloadHex, n, k,     │
  │                          │   directMode, ciphertextHex}     │
  │                          │                                  │
  │                          │  RainMap.fromEncrypted(payload,mk)│
  │                          │  rainMap.unseal(mk)              │
  │                          │  ──► Map<dropID, nodeURL>        │
  │                          │                                  │
  │                          │  GET /drops/{id} ──────────────► │ keeper-1
  │                          │  GET /drops/{id} ──────────────► │ keeper-2
  │                          │  GET /drops/{id} ──────────────► │ keeper-3
  │                          │  ... (up to K valid drops)       │
  │                          │                                  │
  │                          │  DropFactory.verifyOrThrow()    │
  │                          │  (MAC + TTL) for each drop       │
  │                          │                                  │
  │                          │  RainDropsCore.reconstruct()     │
  │                          │  ──► original data               │
  │                          │                                  │
  │  {success, data(base64)} │                                  │
  │ ◄──────────────────────  │                                  │
```

**Optimization:** The witness stops collecting drops once `k` valid ones are obtained. If quorum is not reached, it returns an error with the list of invalid drops.

### 4.3 Verify (VERIFY)

```
Client                    Witness
  │                          │
  │  POST /witness/verify    │
  │  {dropJson, masterKeyHex}│
  │ ──────────────────────►  │
  │                          │  1. Deserialize dropJson
  │                          │  2. drop.isExpired()?
  │                          │  3. DropFactory.verifyOrThrow()
  │                          │     ─► HMAC-SHA256(x‖y‖ttl, mk)
  │                          │     ─► constant-time comparison
  │  {valid, message}        │
  │ ◄──────────────────────  │
```

### 4.4 Storage Node Replication

```
Storage Node A                    Storage Node B
  │                                    │
  │  (local store)                      │
  │  POST /drops {body}                 │
  │  ──► save to H2                     │
  │  ──► ReplicationService             │
  │       └─► async POST /drops ───────►│ B stores copy
  │       └─► async POST /drops ───────►│ C stores copy
  │                                    │
  │  Local read fails:                 │
  │  GET /drops/{id} (404)             │
  │  ──► ReplicationService            │
  │       └─► sync GET /drops/{id} ───►│ B responds (or C)
  │                                    │
```

---

## 5. Network Architecture

### 5.1 Topology

```
                  ┌──────────────┐
                  │   Client     │
                  │ (curl/SDK)   │
                  └──────┬───────┘
                         │ :9080
                  ┌──────┴───────┐
                  │  Witness     │
                  │  :9080       │
                  └──────┬───────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
   ┌──────┴──────┐ ┌────┴──────┐ ┌────┴──────┐
   │  keeper-1   │ │  keeper-2 │ │  keeper-3  │
   │  :9081      │ │  :9082    │ │  :9083     │
   │  H2 DB      │ │  H2 DB    │ │  H2 DB     │
   └─────────────┘ └───────────┘ └────────────┘
          │              │              │
          └──────────────┴──────────────┘
                   Docker Network (rain-net)
```

### 5.2 Ports

| Service | Container Port | Host Port | Purpose |
|---|---|---|---|
| Witness Node | 8080 | 9080 | Coordination API |
| Storage Node 1 | 8080 | 9081 | Storage API |
| Storage Node 2 | 8080 | 9082 | Storage API |
| Storage Node 3 | 8080 | 9083 | Storage API |

### 5.3 Docker Network

All services share the `rain-net` bridge network. Internal DNS names:
- `witness` → Witness Node
- `keeper-1` → Storage Node 1
- `keeper-2` → Storage Node 2
- `keeper-3` → Storage Node 3

---

## 6. Security Model

### 6.1 Security Layers

```
┌────────────────────────────────────────────────────┐
│                  Application Layer                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ API Key Auth │  │    TLS      │  │Rate Limit│ │
│  │ (X-API-Key)  │  │ (optional)  │  │ (future) │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
├────────────────────────────────────────────────────┤
│                Cryptographic Layer                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │  Shamir SSS  │  │ AES-256-GCM  │  │HMAC-SHA256│ │
│  │  GF(p) K-of-N│  │ hybrid mode  │  │ integrity │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
├────────────────────────────────────────────────────┤
│              Infrastructure Layer                    │
│  ┌──────────────┐  ┌──────────────┐               │
│  │ Docker nets  │  │  persistent  │               │
│  │  isolated    │  │  volumes     │               │
│  └──────────────┘  └──────────────┘               │
└────────────────────────────────────────────────────┘
```

### 6.2 Authentication (API Key)

- Activated via `API_KEY` environment variable
- HTTP Header: `X-API-Key: <value>`
- Public endpoints (no auth): `/health`, `/`, `/actuator/*`, `/swagger-ui/**`, `/api-docs/**`, `/h2-console/**`
- If `API_KEY` is empty: open mode (all endpoints accessible)

### 6.3 TLS

- Disabled by default
- Enable with `TLS_ENABLED=true`
- Requires JKS keystore mounted in the container
- Configuration: `server.ssl.key-store`, `server.ssl.key-store-password`

### 6.4 TTL Expiry

- Each drop has a TTL in Unix seconds
- A *reaper* process runs every 60 seconds, deleting expired drops
- Expired drops are irrecoverable — even with the master key

---

## 7. Deployment

### 7.1 Docker Compose (Development/Local)

```bash
# Build and start
docker compose -f docker-compose.yml up -d --build

# Check status
docker compose -f docker-compose.yml ps

# Logs
docker compose -f docker-compose.yml logs -f

# Without authentication
API_KEY="" docker compose -f docker-compose.yml up -d --build

# With authentication
API_KEY="my-secret-key" docker compose -f docker-compose.yml up -d --build
```

### 7.2 Raspberry Pi (ARM64)

```bash
# Clone on Pi
git clone https://github.com/erac73/raindrops-fase1.git
cd raindrops-fase1

# Deploy
docker compose -f docker-compose.yml up -d --build
```

Docker images support `linux/arm64` natively (built with QEMU + Buildx multi-arch).

### 7.3 Automated Deployment

**GitHub Actions:** Workflow `.github/workflows/deploy.yml` enables manual deployment via `workflow_dispatch` with SSH.

**Paramiko Script:** `deploy_pi.py` automates clone/pull + build + deploy on Raspberry Pi.

### 7.4 Environment Variables

| Variable | Default | Service | Description |
|---|---|---|---|
| `API_KEY` | `""` | Storage, Witness | API authentication key |
| `NODE_ID` | `"storage-node"` | Storage | Node identifier |
| `PEER_URLS` | `""` | Storage | Comma-separated peer URLs |
| `STORAGE_URLS` | `""` | Witness | Comma-separated storage node URLs |
| `WITNESS_PORT` | `8080` | Witness | Witness server port |
| `STORAGE_PORT` | `8080` | Storage | Storage server port |
| `TLS_ENABLED` | `"false"` | Storage | Enable TLS |
| `TLS_KEYSTORE` | `""` | Storage | Path to JKS keystore |

---

## 8. Configuration Reference

### 8.1 File Structure

```
raindrops-fase1/
├── raindrops/                      ← Core module
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/raindrops/
│       │   ├── core/              ← SSS, AES-GCM, Drop, RainMap
│       │   └── client/            ← RainClient SDK
│       └── test/java/io/raindrops/core/
├── storage/                        ← Storage module
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/raindrops/storage/
│       │   ├── config/            ← Security, Reaper, Peer config
│       │   ├── controller/        ← REST API
│       │   ├── service/           ← Business logic
│       │   ├── model/             ← JPA entities
│       │   └── repository/        ← Data access
│       └── resources/
│           ├── application.yml
│           └── templates/dashboard.html
├── witness/                        ← Witness module
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/raindrops/witness/
│       │   ├── controller/        ← REST API
│       │   └── service/           ← Orchestration
│       └── resources/application.yml
├── docs/                           ← Documentation and web demo
├── .github/workflows/              ← CI/CD
├── Dockerfile.storage              ← Multi-arch storage build
├── Dockerfile.witness              ← Multi-arch witness build
├── docker-compose.yml              ← Orchestration
├── entrypoint.sh                   ← Container entrypoint
└── prometheus.yml                  ← Prometheus config
```

### 8.2 Building

```bash
# Build core and run tests
mvn -f raindrops-fase1/raindrops/pom.xml install

# Build storage (requires core installed)
mvn -f raindrops-fase1/storage/pom.xml package -DskipTests

# Build witness (requires core installed)
mvn -f raindrops-fase1/witness/pom.xml package -DskipTests

# Run integration tests
mvn -f raindrops-fase1/storage/pom.xml test
```

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026. Architecture document version 0.1.0.*
