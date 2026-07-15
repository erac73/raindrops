<p align="center">
  <img src="assets/raindrops-logo.svg" alt="Rain Drops Logo" width="200"/>
</p>

<h1 align="center">Rain Drops</h1>

<p align="center">
  <em>"A raindrop carries no information about the storm."</em>
</p>

<p align="center">
  <strong>Author:</strong> Edwar Antonio Ramírez Castillo &nbsp;|&nbsp;
  <strong>Status:</strong> Phase 4 — Production <img src="https://img.shields.io/badge/status-beta-blue?style=flat-square" alt="Beta"/>
</p>

<p align="center">
  <a href="README_ES.md"><img src="https://img.shields.io/badge/Español-README-blue?style=flat-square" alt="Español"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"/></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Docker-multi--arch-blue?style=flat-square" alt="Docker multi-arch"/>
  <a href="https://erac73.github.io/raindrops/"><img src="https://img.shields.io/badge/Project-Page-blue?style=flat-square" alt="Project Page"/></a>
  <img src="https://img.shields.io/badge/Phase-4%20Production-purple?style=flat-square" alt="Phase 4"/>
</p>

---

## What is Rain Drops?

**Rain Drops** is a **distributed information storage model** based on **threshold cryptography**. Data is fragmented into cryptographic micro-units called **drops** that, individually, are indistinguishable from random noise. The original data can only be reconstituted when a sufficient number of drops converge under verified conditions.

The metaphor is precise: just as a single raindrop carries no information about the storm, each drop is, in isolation, pure noise. Only when **K of N drops** are combined does the data emerge.

> **This is not encryption layered on top of storage. Confidentiality is a structural property of the model.**

---

## Why Rain Drops?

### The Problem with Traditional Storage

| Problem | Traditional Solution | Limitation |
|---|---|---|
| Plaintext data on servers | Disk encryption | Root attacker has the keys |
| Centralized backup | Replicas | Single point of failure or seizure |
| Multi-user access | ACLs / RBAC | System administrator can access |
| Compliance (GDPR/HIPAA) | Policies and procedures | Security depends on everyone following them |
| Ransomware | Air-gapped backups | Expensive, complex, not scalable |

### The Rain Drops Solution

```
┌─────────────────────────────────────────────────────────────────┐
│  ORIGINAL DATA                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  "My secret"                                             │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AES-256-GCM (encryption + integrity)                    │  │
│  │  → Ciphertext (random noise)                             │  │
│  │  → AES Key (secret to fragment)                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Shamir SSS (K,N) — Threshold over GF(2^521-1)          │  │
│  │  → N drops individually useless                          │  │
│  │  → Only K drops can reconstruct the AES key              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│              ┌───────────────┼───────────────┐                  │
│              ▼               ▼               ▼                  │
│         ┌─────────┐    ┌─────────┐    ┌─────────┐              │
│         │ Drop 1  │    │ Drop 2  │    │ Drop N  │              │
│         │ (noise) │    │ (noise) │    │ (noise) │              │
│         └─────────┘    └─────────┘    └─────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

**Guaranteed Mathematical Properties:**

- **Perfect secrecy**: With fewer than K drops, the secret's distribution is uniform over GF(p). It's not "hard to crack" — it's **mathematically impossible**.
- **IND-CCA2**: Secure against chosen-ciphertext attacks (highest standard security).
- **Structural integrity**: Each drop carries HMAC-SHA256. The Witness Node verifies everything before reconstruction.
- **Temporal expiry**: Drops carry TTL. An expired drop is irrecoverable.

📖 **Full documentation:** [Architecture Guide](docs/ARCHITECTURE.md) · [API Reference](docs/API.md) · [Contributing Guide](CONTRIBUTING.md)

---

## System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CLIENT                                          │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  RainClient SDK                                                 │  │
│  │  → store(data, N, K, ttlDays)  →  RainMapId + MasterKey        │  │
│  │  → retrieve(rainMapId, masterKey)  →  data                      │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                              │                                        │
│                              ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  Witness Node (Stateless Verifier)                              │  │
│  │  → Verifies integrity of each drop before reconstruction        │  │
│  │  → Coordinates storage and reconstruction                       │  │
│  │  → Stateless: stores no data, only verifies                     │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│              │                      │                      │           │
│              ▼                      ▼                      ▼           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐      │
│  │ Storage Node 1  │  │ Storage Node 2  │  │ Storage Node 3  │      │
│  │ (keeper-1)      │  │ (keeper-2)      │  │ (keeper-3)      │      │
│  │                 │  │                 │  │                 │      │
│  │ • Drops storage │  │ • Drops storage │  │ • Drops storage │      │
│  │ • RainMap index │  │ • RainMap index │  │ • RainMap index │      │
│  │ • TTL Reaper    │  │ • TTL Reaper    │  │ • TTL Reaper    │      │
│  │ • Replication   │  │ • Replication   │  │ • Replication   │      │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘      │
└────────────────────────────────────────────────────────────────────────┘
```

### Storage Flow (STORE)

```
1. Client sends data to Witness Node
2. Witness generates random AES key → encrypts data → SSS on key
3. Witness distributes N drops to N storage nodes
4. Witness creates RainMap (encrypted index: drop_id → node_url)
5. Witness stores RainMap on primary node
6. Returns: rainMapId + masterKey (to client)
```

### Reconstruction Flow (RECONSTRUCT)

```
1. Client sends rainMapId + masterKey to Witness
2. Witness decrypts RainMap → gets location of each drop
3. Witness collects drops from storage nodes
4. Witness VERIFIES each drop (HMAC + TTL) ← CRITICAL
5. With K valid drops → Lagrange interpolation → AES key
6. Witness decrypts ciphertext with reconstructed AES key
7. Returns: original data to client
```

---

## Security Properties

| Property | Guarantee | Mechanism |
|---|---|---|
| **Perfect secrecy** | With K-1 drops: zero information | Shamir SSS over GF(p), p = 2^521-1 |
| **IND-CCA2** | Ciphertext indistinguishable from noise | AES-256-GCM + 256-bit random key |
| **Integrity** | Tampering detected | HMAC-SHA256 on every drop |
| **Authenticity** | Drops verified before reconstruct | Witness Node verifies each drop's MAC |
| **Blind identity** | No correlation between drops | IDs = HMAC(nonce, masterKey) |
| **Temporal expiry** | Drops unusable after TTL | TTL encoded in each drop + automatic Reaper |
| **Resilience** | Tolerance to N-K node failures | Drops distributed + automatic replication |
| **Zero-knowledge storage** | Storage node NEVER sees plaintext | Only stores drops (noise) |

---

## System Components

### raindrops-core (Cryptographic Core)

| Class | Responsibility |
|---|---|
| `ShamirSSS` | Shamir's Secret Sharing over GF(2^521-1) |
| `HybridScheme` | Hybrid AES-256-GCM + SSS scheme for arbitrary data |
| `RainDropsCore` | Main facade: orchestrates DROP and RECONSTRUCT |
| `Drop` | Drop model: (id, x, y, mac, ttl) |
| `DropFactory` | Drop creation and verification |
| `RainMap` | Encrypted index mapping drop_ids to storage node URLs |
| `RainClient` | Client SDK for DROP/RECONSTRUCT operations over network |

### Storage Node

| Component | Responsibility |
|---|---|
| `DropService` | Drop CRUD with automatic TTL |
| `RainMapService` | RainMap management with replication |
| `ReplicationService` | Drop and RainMap replication between peers |
| `ReaperConfig` | Automatic cleanup of expired drops |
| `SecurityConfig` | API Key authentication + public endpoints |
| `DashboardController` | Monitoring UI (Thymeleaf) |

### Witness Node

| Component | Responsibility |
|---|---|
| `WitnessService` | Verification, storage, and reconstruction logic |
| `WitnessController` | REST API: /witness/store, /witness/reconstruct, /witness/verify |

---

## Phase 4: Production

### Monitoring & Observability

```
┌─────────────────────────────────────────────────────────────┐
│  Prometheus + Grafana                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Drops Store  │  │ Drops Reconstruct│  │ Drops Verify │     │
│  │ Rate         │  │ Rate              │  │ Rate         │     │
│  │ Latency P99  │  │ Latency P99       │  │ Latency P99  │     │
│  │ Errors       │  │ Errors            │  │ Errors       │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Storage Node │  │ Witness Node  │  │ Replication  │     │
│  │ Health       │  │ Health        │  │ Lag          │     │
│  │ Disk Usage   │  │ Active Conns  │  │ Queue Size   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

**Exposed Metrics (Prometheus):**

| Metric | Type | Description |
|---|---|---|
| `raindrops_store_total` | Counter | Total STORE operations |
| `raindrops_reconstruct_total` | Counter | Total RECONSTRUCT operations |
| `raindrops_verify_total` | Counter | Total VERIFY operations |
| `raindrops_store_duration_seconds` | Histogram | STORE latency |
| `raindrops_reconstruct_duration_seconds` | Histogram | RECONSTRUCT latency |
| `raindrops_drops_expired_total` | Counter | Drops expired by Reaper |
| `raindrops_replication_lag_seconds` | Gauge | Replication lag between peers |
| `raindrops_storage_nodes_up` | Gauge | Active storage nodes |

### Key Rotation

```java
// masterKey is generated per operation and never stored
// API Keys rotated via environment variable
// Drops expire automatically by TTL
// Witness Node stores no state → zero attack surface
```

### Rate Limiting

```yaml
# Default configuration
rate-limiting:
  store: 100/min      # STORE operations per minute
  reconstruct: 50/min # RECONSTRUCT operations per minute
  verify: 200/min     # VERIFY operations per minute
```

### Health Checks

```bash
# All nodes expose:
GET /health           # Service status
GET /actuator/health  # Spring Boot Actuator (detailed)
GET /actuator/prometheus  # Prometheus metrics
```

---

## Quick Start

### Requirements

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for deployment)

### Build and Test

```bash
# Build cryptographic core + run tests
mvn -f raindrops-fase1/raindrops/pom.xml install

# Build storage node + run tests
mvn -f raindrops-fase1/storage/pom.xml test

# Build witness node + run tests
mvn -f raindrops-fase1/witness/pom.xml test
```

### Deploy with Docker

```bash
# Development (no authentication)
docker compose -f docker-compose.yml up -d --build

# Production (with API Key)
API_KEY=my-secret-key docker compose -f docker-compose.yml up -d --build
```

### Deploy on Raspberry Pi

```bash
# Clone on Pi
ssh serpico@<pi-ip>
git clone http://ERAC@<gitea-ip>:3000/ERAC/raindrops-fase1.git
cd raindrops-fase1

# Build multi-arch (ARM64)
docker compose -f docker-compose.yml build

# Run
docker compose -f docker-compose.yml up -d

# Verify
curl http://localhost:9081/health
curl http://localhost:9080/health
```

---

## API Usage
---

## Quick Demo

Test Rain Drops in 60 seconds using the interactive web demo.

### Step 1 — Open the Demo

Open the demo page in your browser:

- **Local:** `http://localhost:9089/demo.html`
- **Raspberry Pi:** `http://<pi-ip>:8089/demo.html`
- **GitHub Pages:** `https://erac73.github.io/raindrops/demo.html`

### Step 2 — Configure the Witness Node URL

At the top of the demo, set the **Witness Node URL**:

```
http://localhost:9080        # local
http://<pi-ip>:9080          # Raspberry Pi
```

> The Witness Node coordinates storage and reconstruction. It must be running.

### Step 3 — Store Data

1. Enter any text in the **Data to store** field (e.g., `My secret password`)
2. Set the parameters:

| Parameter | Meaning | Recommended |
|---|---|---|
| **N** (total drops) | Total fragments to create | 5 |
| **K** (threshold) | Minimum drops needed to reconstruct | 3 |
| **TTL** (days) | Drops expire after this time | 30 |

3. Click **Store**
4. The demo returns:
   - **RainMap ID** — Encrypted index of where your drops are stored
   - **Master Key** — Your decryption key (save this!)

> These two values are all you need to reconstruct later. The Witness Node stores nothing.

### Step 4 — Reconstruct

1. The **RainMap ID** and **Master Key** fields are auto-filled after storing
2. Click **Reconstruct**
3. The original data appears in the response panel

### Step 5 — Verify It Works

Try these experiments:

- **Remove a drop:** Delete one storage node and reconstruct — it still works (K of N)
- **Wrong key:** Change one character in the Master Key — reconstruction fails
- **Expired drop:** Set TTL=1, wait a day, reconstruct — drops are gone

### Understanding the Response

```json
{
  "success": true,
  "data": "TXkgc2VjcmV0IGRhdGE=",
  "n": 5,
  "k": 3
}
```

### What Happens Under the Hood

```
STORE:
  Client → Witness → [AES-256-GCM encrypt] → [Shamir SSS split] → Distribute to N nodes
  Witness returns: rainMapId + masterKey

RECONSTRUCT:
  Client → Witness (rainMapId + masterKey) → [Locate drops] → [Verify HMAC] → [Lagrange interpolation] → [AES decrypt]
  Witness returns: original data
```

### RainClient SDK (Java)

```xml
<dependency>
    <groupId>io.raindrops</groupId>
    <artifactId>raindrops-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.raindrops.client.RainClient;

// Connect to 3 storage nodes
List<String> nodes = List.of(
    "http://localhost:9081",
    "http://localhost:9082",
    "http://localhost:9083"
);

try (RainClient client = new RainClient(nodes)) {
    // Store: 5 drops, K=3, TTL 30 days
    String rainMapId = client.store(
        "My secret".getBytes(), 5, 3, 30
    );
    System.out.println("Stored: " + rainMapId);

    // Retrieve
    byte[] data = client.retrieve(rainMapId, masterKey);
    System.out.println("Retrieved: " + new String(data));
}
```

### REST API (cURL)

```bash
# ─── STORE via Witness ────────────────────────────────
curl -X POST http://localhost:9080/witness/store \
  -H "Content-Type: application/json" \
  -d '{
    "data": "'$(echo -n "My secret data" | base64)'",
    "n": 5,
    "k": 3,
    "ttlDays": 30
  }'

# Response:
# {
#   "rainMapId": "a1b2c3...",
#   "masterKeyHex": "012345..."
# }

# ─── RECONSTRUCT via Witness ──────────────────────────
curl -X POST http://localhost:9080/witness/reconstruct \
  -H "Content-Type: application/json" \
  -d '{
    "rainMapId": "a1b2c3...",
    "masterKeyHex": "012345..."
  }'

# Response:
# {
#   "success": true,
#   "data": "TXkgc2VjcmV0IGRhdGE=",  // base64
#   "n": 5,
#   "k": 3
# }

# ─── VERIFY a drop ────────────────────────────────────
curl -X POST http://localhost:9080/witness/verify \
  -H "Content-Type: application/json" \
  -d '{
    "dropJson": "{...}",
    "masterKeyHex": "012345..."
  }'

# ─── Health check ─────────────────────────────────────
curl http://localhost:9081/health
# {"status":"UP","service":"storage","node":"keeper-1"}

curl http://localhost:9080/health
# {"status":"UP","service":"witness"}
```

---

## Use Cases

### 1. Ransomware-Resistant Backup

```
Problem:  Ransomware encrypts your backups. Pay or lose data.
Solution: Rain Drops fragments backups across 5 nodes. K=3 to restore.
          An attacker stealing 2 nodes gets only noise.
Result:   Your backups are useless to the attacker.
```

### 2. Distributed Confidential Storage

```
Problem:  Sensitive data (medical, financial) on a central server.
Solution: Distribute fragments across jurisdictions/entities.
          K entities must cooperate to access.
Result:   No single entity can access the data alone.
```

### 3. Enterprise Secret Management

```
Problem:  API keys, certificates, secrets in a central vault.
Solution: Each secret fragmented across N nodes.
          K=3 to reconstruct (e.g., CTO + Director + Auditor).
Result:   No individual can access secrets alone.
```

### 4. Automated Compliance

```
Problem:  GDPR/HIPAA require data to be inaccessible.
Solution: Confidentiality is structural, not procedural.
          Data is irrecoverable without K parts + masterKey.
Result:   Compliance by design, not by policy.
```

### 5. Zero-Knowledge Storage

```
Problem:  Cloud provider can see your data.
Solution: Storage node NEVER sees plaintext.
          Only stores drops (random noise).
Result:   Not even the system administrator can access.
```

---

## Production Security

### Deployment Checklist

- [ ] API Key enabled on all storage nodes
- [ ] TLS enabled (keystore mounted via Docker volume)
- [ ] Rate limiting configured for expected load
- [ ] Prometheus + Grafana configured for monitoring
- [ ] Alerts configured (down nodes, slow replication)
- [ ] TTL appropriate for use case (no longer than necessary)
- [ ] RainMap backup outside main network
- [ ] API Key rotation scheduled

### Recommendations

1. **Never use K=2 in production** — Minimum K=3 for tolerance to one malicious drop
2. **Shorter TTL is better** — Smaller attack window
3. **Monitor replication** — If a node is slow, drops may be lost
4. **Backup the RainMap** — Without the RainMap, drops are useless
5. **Audit logs** — Who performed STORE/RECONSTRUCT and when

---

## Project Structure

```
raindrops-fase1/
├── raindrops/                    ← Cryptographic core
│   └── src/main/java/io/raindrops/
│       ├── core/                 ← ShamirSSS, HybridScheme, RainDropsCore
│       └── client/               ← RainClient SDK
├── storage/                      ← Storage Node
│   └── src/main/java/io/raindrops/storage/
│       ├── controller/           ← REST + Dashboard UI
│       ├── service/              ← DropService, RainMapService, ReplicationService
│       ├── model/                ← JPA entities
│       ├── repository/           ← Spring Data repositories
│       └── config/               ← PeerConfig, ReaperConfig, SecurityConfig
├── witness/                      ← Witness Node
│   └── src/main/java/io/raindrops/witness/
│       ├── controller/           ← REST API
│       └── service/              ← WitnessService
├── docs/                         ← Documentation and demo web
├── Dockerfile.storage            ← Multi-stage ARM64/AMD64 build
├── Dockerfile.witness            ← Witness Node Docker build
├── docker-compose.yml            ← 3-node + witness orchestration
└── prometheus.yml                ← Prometheus configuration
```

---

## Roadmap

| Phase | Status | Description |
|---|---|---|
| Phase 1 | [DONE] Complete | Cryptographic core: ShamirSSS, HybridScheme, RainDropsCore |
| Phase 2 | [DONE] Complete | Storage Node: Spring Boot, JPA, H2, replication, Dashboard |
| Phase 3 | [DONE] Complete | Witness Node: Verification, Auth/TLS, RainMap replication, CI/CD |
| **Phase 4** | [...] **In Progress** | **Production: Monitoring, Rate Limiting, Health Checks, Hardening** |
| Phase 5 | TODO Planned | Geographic distribution, node consensus, key rotation |
| Phase 6 | TODO Planned | Multi-language clients (Python, JS, Go), public API |

---

## Academic Publications

> Ramírez Castillo, E. A. (2026). *Rain Drops: A Theoretical Model for Distributed Information Storage Based on Threshold Cryptography and Semantic Fragmentation.*

> Ramírez Castillo, E. A. (2026). *Rain Drops — Phase 1: Cryptographic Core Implementation.*

---

## Contributing

Contributions are welcome. Please read `CONTRIBUTING.md` before submitting a Pull Request.

---

## License

This project is released under the **MIT License**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
