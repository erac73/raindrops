<p align="center">
  <img src="assets/raindrops-logo.svg" alt="Rain Drops Logo" width="200"/>
</p>

<h1 align="center">Rain Drops</h1>

<p align="center">
  <em>"A raindrop carries no information about the storm."</em>
</p>

<p align="center">
  <strong>Author:</strong> Edwar Antonio Ramírez Castillo &nbsp;|&nbsp;
  <strong>Status:</strong> Fase 3 — Witness Node <img src="https://img.shields.io/badge/status-alpha-yellow?style=flat-square" alt="Alpha"/>
</p>

<p align="center">
  <a href="README_ES.md"><img src="https://img.shields.io/badge/Español-README-blue?style=flat-square" alt="Español"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"/></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Docker-multi--arch-blue?style=flat-square" alt="Docker multi-arch"/>
</p>

---

## What is Rain Drops?

Rain Drops is a **distributed information storage model** based on **threshold cryptography**. Data is fragmented into cryptographic micro-units — called **drops** — that carry no individual meaning. The original data can only be reconstituted when a sufficient number of drops converge under verified conditions.

The metaphor is precise: just as a single raindrop contains no information about the storm, each drop is, in isolation, indistinguishable from random noise. Only when **K of N drops** are combined does the data emerge.

This is not encryption layered on top of storage. **Confidentiality is a structural property of the model.**

---

## Architecture Overview

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   RainClient│────▶│  Witness Node│────▶│ Storage Node │
│   (SDK)     │     │  (verifier)  │     │   keeper-1   │
└─────────────┘     │              │     └──────────────┘
                    │  /witness/   │     ┌──────────────┐
                    │  store       │────▶│ Storage Node │
                    │  /witness/   │     │   keeper-2   │
                    │  reconstruct │     └──────────────┘
                    │  /witness/   │     ┌──────────────┐
                    │  verify      │────▶│ Storage Node │
                    └──────────────┘     │   keeper-3   │
                                         └──────────────┘
```

| Component | Description |
|---|---|
| **RainClient SDK** | Java library for DROP/RECONSTRUCT operations. Coordinates with storage nodes or witness. |
| **Witness Node** | Stateless coordinator that verifies drop integrity (MAC, TTL) before RECONSTRUCT. Prevents malicious nodes from injecting fake data. |
| **Storage Node** | Spring Boot service storing drops and RainMaps. Replicates to peers. TTL-based reaper. |

All nodes expose:
- Swagger UI at `/swagger-ui.html`
- OpenAPI spec at `/api-docs`
- Prometheus metrics at `/actuator/prometheus`
- Health at `/health`

📖 **Full documentation:** [Architecture Guide](docs/ARCHITECTURE.md) · [API Reference](docs/API.md) · [Contributing Guide](CONTRIBUTING.md)

---

## Core Concepts

| Concept | Description |
|---|---|
| **Drop** | Cryptographic micro-unit: `(id, x, y, mac, ttl)`. Individually meaningless. |
| **Rain** | A set of N drops generated from one datum, requiring K to reconstruct. |
| **Rain Map** | AES-GCM sealed index mapping drop IDs to storage node URLs. |
| **Threshold K** | Minimum drops needed to reconstruct. With K-1 drops: zero information. |
| **Witness Node** | Stateless coordinator that verifies drop integrity before reconstruction. |

---

## Security Properties

| Property | Guarantee |
|---|---|
| **Perfect secrecy** | With fewer than K drops, the secret is information-theoretically hidden. |
| **IND-CCA2** | Hybrid scheme (AES-256-GCM + SSS) is secure against chosen-ciphertext attacks. |
| **Integrity** | Each drop carries an HMAC-SHA256 MAC. Witness Node verifies all drops before RECONSTRUCT. |
| **Blind identity** | Drop IDs are `HMAC(nonce, masterKey)` — no visible correlation. |
| **Temporal expiry** | Drops carry a TTL. Expired drops are irrecoverable. |

---

## Project Structure

```
raindrops-fase1/
├── raindrops/          ← Core crypto module (SSS, AES-GCM, HMAC, RainClient SDK)
│   └── src/main/java/io/raindrops/
│       ├── core/       ← ShamirSSS, Drop, DropFactory, HybridScheme, RainDropsCore
│       └── client/     ← RainClient SDK
├── storage/            ← Storage Node (Spring Boot, JPA, H2, replication)
│   └── src/main/java/io/raindrops/storage/
│       ├── controller/ ← REST endpoints, Dashboard UI (Thymeleaf)
│       ├── service/    ← DropService, RainMapService, ReplicationService
│       ├── model/      ← JPA entities
│       ├── repository/ ← Spring Data repositories
│       └── config/     ← PeerConfig, ReaperConfig, SecurityConfig
├── witness/            ← Witness Node (Spring Boot, drop verification)
│   └── src/main/java/io/raindrops/witness/
│       ├── controller/ ← REST endpoints
│       └── service/    ← WitnessService (verify, reconstruct, store)
├── Dockerfile.storage  ← Multi-stage ARM64/AMD64 build
├── Dockerfile.witness  ← Witness Node Docker build
├── docker-compose.yml  ← 3-node storage + witness orchestration
└── entrypoint.sh       ← Runtime permission fixer
```

---

## Getting Started

### Requirements

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for deployment)

### Build and test

```bash
# Build core + run tests
mvn -f raindrops-fase1/raindrops/pom.xml install

# Build storage + run tests
mvn -f raindrops-fase1/storage/pom.xml test
```

### Run locally with Docker

```bash
docker compose -f docker-compose.yml up -d --build
```

Endpoints:
- Witness: `http://localhost:9080/witness/store`
- Storage 1: `http://localhost:9081/swagger-ui.html`
- Storage 2: `http://localhost:9082/swagger-ui.html`
- Storage 3: `http://localhost:9083/swagger-ui.html`
- Prometheus: `http://localhost:9081/actuator/prometheus`
- Dashboard: `http://localhost:9081/`

### Using RainClient SDK

```xml
<dependency>
    <groupId>io.raindrops</groupId>
    <artifactId>raindrops-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.raindrops.client.RainClient;

List<String> nodes = List.of("http://localhost:9081", "http://localhost:9082", "http://localhost:9083");
try (RainClient client = new RainClient(nodes)) {
    // Store data (5 drops, K=3, 30-day TTL)
    String rainMapId = client.store("Hello Rain Drops!".getBytes(), 5, 3, 30);
    System.out.println("Stored: " + rainMapId);

    // Retrieve data
    byte[] data = client.retrieve(rainMapId, masterKey);
    System.out.println(new String(data));
}
```

### Using the Witness Node API

```bash
# Store data via witness (automatically distributes to storage nodes)
curl -X POST http://localhost:9080/witness/store \
  -H "Content-Type: application/json" \
  -d '{"data": "'$(echo -n "My secret data" | base64)'", "n": 5, "k": 3, "ttlDays": 30}'

# Reconstruct via witness (verifies all drops before reconstruction)
curl -X POST http://localhost:9080/witness/reconstruct \
  -H "Content-Type: application/json" \
  -d '{"rainMapId": "<rainMapId>", "masterKeyHex": "<masterKeyHex>"}'

# Verify a specific drop
curl -X POST http://localhost:9080/witness/verify \
  -H "Content-Type: application/json" \
  -d '{"dropJson": "<drop json>", "masterKeyHex": "<masterKeyHex>"}'
```

---

## Authentication & TLS

- Set `API_KEY` environment variable to enable API key authentication on storage nodes
- All requests must include `X-API-Key` header
- Public endpoints (health, swagger) remain open
- TLS can be enabled by mounting a keystore and setting `TLS_ENABLED=true`

---

## RainMap Replication

RainMaps are automatically replicated to all peer nodes when stored, just like drops. This ensures that the RainMap remains accessible even if the originating node fails.

---

## Docker Images

Multi-arch images (`linux/amd64` and `linux/arm64`) are built automatically:
- **Raindrops Storage**: `raindrops/storage:latest`
- **Raindrops Witness**: `raindrops/witness:latest`

---

## Publications

> Ramírez Castillo, E. A. (2026). *Rain Drops: Un Modelo Teórico de Almacenamiento Distribuido de Información Basado en Criptografía de Umbral y Fragmentación Semántica.*

> Ramírez Castillo, E. A. (2026). *Rain Drops — Fase 1: Implementación del Núcleo Criptográfico.*

---

## License

This project is released under the **MIT License**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
