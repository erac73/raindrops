<p align="center">
  <img src="assets/raindrops-logo.svg" alt="Rain Drops Logo" width="200"/>
</p>

<h1 align="center">🌧️ Rain Drops</h1>

<p align="center">
  <em>"A raindrop carries no information about the storm."</em>
</p>

<p align="center">
  <strong>Author:</strong> Edwar Antonio Ramírez Castillo &nbsp;|&nbsp;
  <strong>Status:</strong> Phase 1 — Cryptographic Core ✅ &nbsp;|&nbsp;
  Phase 2 — Storage Node 🔜
</p>

<p align="center">
  <a href="README_ES.md"><img src="https://img.shields.io/badge/Español-README-blue?style=flat-square" alt="Español"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"/></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Python-3.9%2B-blue?style=flat-square" alt="Python 3.9+"/>
</p>

---

## What is Rain Drops?

Rain Drops is a distributed information storage model based on threshold cryptography. Data is fragmented into cryptographic micro-units — called **drops** — that carry no individual meaning. The original data can only be reconstituted when a sufficient number of drops converge under verified conditions.

The metaphor is precise: just as a single raindrop contains no information about the storm, each drop is, in isolation, indistinguishable from random noise. Only when **K of N drops** are combined does the data emerge.

This is not encryption layered on top of storage. **Confidentiality is a structural property of the model.**

---

## Core Concepts

| Concept | Description |
|---|---|
| **Drop** | Cryptographic micro-unit: `(id, x, y, mac, ttl)`. Individually meaningless. |
| **Rain** | A set of N drops generated from one datum, requiring K to reconstruct. |
| **Rain Map** | Encrypted index mapping drop IDs to storage node URLs. |
| **Threshold K** | Minimum drops needed to reconstruct. With K-1 drops: zero information. |
| **Witness Node** | Stateless coordinator that orchestrates reconstruction. |
| **Oracle** | External service that signs access conditions (time, identity, multisig). |

---

## Security Properties

| Property | Guarantee |
|---|---|
| **Perfect secrecy** | With fewer than K drops, the secret is information-theoretically hidden — not just computationally. |
| **IND-CCA2** | Hybrid scheme (AES-256-GCM + SSS) is secure against chosen-ciphertext attacks. |
| **Integrity** | Each drop carries an HMAC-SHA256 MAC. Tampering is detected with probability 1 − 2⁻²⁵⁶. |
| **Blind identity** | Drop IDs are `HMAC(nonce, masterKey)` — no visible correlation to the data or to each other. |
| **Temporal expiry** | Drops carry a TTL. Expired drops are irrecoverable by design. |

These properties are formally proved in the theoretical paper (see [Publications](#publications)).

---

## Architecture

```
RainDropsCore          ← single entry point: DROP / RECONSTRUCT
    │
    ├── ShamirSSS      ← (K,N)-threshold SSS over GF(2^521-1)
    │       └── Lagrange interpolation, Horner evaluation
    │
    ├── HybridScheme   ← AES-256-GCM (Bouncy Castle 1.77)
    │
    └── DropFactory    ← HMAC-SHA256, blind identity, TTL
            └── Drop   ← immutable data structure
```

### How DROP works

```
Data D
  │
  ├─ if |D| ≤ 65 bytes ──→ S = D (direct mode)
  │
  └─ if |D| > 65 bytes ──→ k_AES = random 256-bit key
                            C = AES-256-GCM(k_AES, D)   ← store anywhere
                            S = k_AES                    ← fragment with SSS
  │
  ▼
f(x) = S + a₁x + ... + a_{K-1}x^{K-1}  mod p     ← random polynomial
  │
  ├── drop_1 = (HMAC(nonce₁, mk), 1, f(1), MAC, ttl)  → Node 1
  ├── drop_2 = (HMAC(nonce₂, mk), 2, f(2), MAC, ttl)  → Node 2
  │   ...
  └── drop_N = (HMAC(nonceN, mk), N, f(N), MAC, ttl)  → Node N

Returns: (RainMap sealed with AES-GCM, masterKey)
```

### How RECONSTRUCT works

```
Unseal Rain Map → verify access policy → issue quorum token
  │
  ▼
Collect K drops in parallel → verify each MAC → verify TTL
  │
  ▼
Lagrange interpolation: S = Σᵢ yᵢ · Lᵢ(0)  mod p
  │
  ├─ direct mode ──→ D = S
  └─ hybrid mode ──→ D = AES-256-GCM-Decrypt(S, C)
  │
  ▼
Erase S and drops from memory → return D
```

---

## Project Structure

```
raindrops-fase1/
├── README.md               ← Documentation (English)
├── README_ES.md            ← Documentation (Spanish)
├── LICENSE                 ← MIT License
├── .gitignore
├── assets/
│   └── raindrops-logo.svg  ← Project logo
│
└── raindrops/
    ├── pom.xml
    ├── raindrops.py         ← Python implementation + node simulator
    └── src/
        ├── main/java/io/raindrops/core/
        │   ├── ShamirSSS.java        ← SSS over GF(2^521-1)
        │   ├── Drop.java             ← immutable drop structure
        │   ├── DropFactory.java      ← creation + HMAC verification
        │   ├── HybridScheme.java     ← AES-256-GCM
        │   └── RainDropsCore.java    ← DROP / RECONSTRUCT facade
        ├── main/java/io/raindrops/demo/
        │   └── RainDropsDemo.java    ← Interactive CLI demo
        └── test/java/io/raindrops/core/
            ├── ShamirSSSTest.java    ← 12 tests
            ├── HybridSchemeTest.java ← 5 tests
            └── RainDropsCoreTest.java← 12 tests
```

---

## Getting Started

### Requirements

- Java 17+
- Maven 3.8+

### Build and test

```bash
git clone https://github.com/erac73/raindrops-fase1.git
cd raindrops-fase1/raindrops
mvn test
```

Expected output:

```
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Run interactive demo (Java)

```bash
cd raindrops-fase1/raindrops
mvn compile exec:java -Dexec.mainClass="io.raindrops.demo.RainDropsDemo"
```

### Run Python implementation

```bash
cd raindrops-fase1/raindrops
python raindrops.py
```

### Quick example

```java
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainDropsCore.RainResult;

// Fragment data into 7 drops, requiring 3 to reconstruct (1-year TTL)
byte[] data    = "Confidential medical record".getBytes();
RainResult rain = RainDropsCore.drop(data, 7, 3, 365);

System.out.println(rain);
// RainResult{n=7, k=3, mode=hybrid, drops=7}

// Reconstruct using only 3 of the 7 drops (simulating 4 node failures)
byte[] recovered = RainDropsCore.reconstruct(
    rain.getDrops().subList(0, 3),
    rain.getMasterKey(),
    rain.getCiphertext(),
    rain.getK(),
    rain.isDirectMode()
);

System.out.println(new String(recovered));
// Confidential medical record
```

---

## Test Coverage

| Test class | Tests | What is verified |
|---|---|---|
| `ShamirSSSTest` | 21 | Mathematical correctness, perfect secrecy, byte conversion, parameter validation |
| `HybridSchemeTest` | 6 | Encrypt/decrypt round-trip, IND property, tamper detection, key zeroization |
| `RainDropsCoreTest` | 14 | Full flow, N-K resilience, integrity, TTL, cross-rain independence |
| **Total** | **41** | — |

Notable tests:

- **`singleShareRevealsNothing`** — runs 100 trials; no single drop reconstructs the secret (probability of false positive: 100/2⁵²¹)
- **`toleratesLossOfNMinusK`** — drops 4 of 7 nodes; reconstructs correctly with exactly K=3 remaining
- **`differentRainsAreIndependent`** — verifies drops cannot be mixed across rains with different master keys

---

## Roadmap

| Phase | Component | Status |
|---|---|---|
| **1** | Cryptographic core (SSS, AES-GCM, HMAC, drops) | ✅ Done |
| **2** | Storage Node — REST API (Spring Boot) + SQLite + TTL Reaper | 🔜 Next |
| **3** | Rain Map (AES-GCM sealed) + Witness Node + quorum protocol | 📋 Planned |
| **4** | Gossip Protocol — autonomous node discovery + reputation | 📋 Planned |
| **5** | Client SDK — high-level API + declarative YAML policies | 📋 Planned |
| **6** | Oracles — Time, Identity, Multisig, HTTP, Composite | 📋 Planned |
| **7** | Proof of Rain — zero-knowledge proofs over fragmented data | 📋 Planned |

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Bouncy Castle | 1.77 | AES-256-GCM, HMAC-SHA256 |
| JUnit Jupiter | 5.10.2 | Test framework |
| AssertJ | 3.25.3 | Fluent test assertions |

---

## Publications

This implementation is based on the following theoretical paper:

> Ramírez Castillo, E. A. (2026). *Rain Drops: Un Modelo Teórico de Almacenamiento Distribuido de Información Basado en Criptografía de Umbral y Fragmentación Semántica.* Propuesta teórica.

The implementation paper documenting Phase 1 design decisions, security property preservation and test results:

> Ramírez Castillo, E. A. (2026). *Rain Drops — Fase 1: Implementación del Núcleo Criptográfico.* Artículo de implementación.

---

## Known Limitations (Phase 1)

- **Direct mode trailing zeros** — data ending in `0x00` bytes may be truncated in direct mode (≤65 bytes). Use hybrid mode or prefix data length. Resolved in Phase 2.
- **In-memory master key** — `masterKey` is returned directly in `RainResult`. In the full system it lives only inside a sealed Rain Map. Resolved in Phase 3.
- **BigInteger non-constant-time** — Lagrange interpolation uses `BigInteger` which is not constant-time, potentially leaking timing information. Mitigation requires constant-time field arithmetic (future work).
- **SecureRandom entropy** — on containers without hardware RNG, early random output may be predictable. Use `haveged` or equivalent entropy daemon.

---

## License

This project is released under the **MIT License**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
