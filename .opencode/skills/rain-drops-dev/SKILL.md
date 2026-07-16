---
name: rain-drops-dev
description: Rain Drops project development guide - architecture, conventions, bugs, commands
license: MIT
compatibility: opencode
metadata:
  audience: developers
  project: raindrops
  language: java
---

## What I do

Guide development for the Rain Drops distributed cryptographic storage project.

## Project Architecture

```
raindrops-fase1/
├── raindrops/          # Core module (ShamirSSS, RainMap, Drop, HybridScheme)
├── storage/            # Storage nodes (Spring Boot + H2 + JPA)
├── witness/            # Witness/orchestrator node (Spring Boot)
├── raindrops.py        # Python SDK reference implementation
└── docs/               # Static documentation (nginx)
```

### Module Relationships
```
Client (RainClient) → Witness (REST) → Storage Nodes (REST)
                                         ├── DropRepository (H2)
                                         └── RainMapRepository (H2)
```

## Key Classes

| Class | Module | Purpose |
|-------|--------|---------|
| `ShamirSSS` | core | SSS over GF(2^521-1), split/combine |
| `RainMap` | core | Encrypted index (dropId→nodeUrl), AES-256-GCM |
| `RainDropsCore` | core | Orchestrates drop()/reconstruct() |
| `Drop` | core | Share unit: (id, x, y, mac, ttl) |
| `DropFactory` | core | Creates/verifies drops with HMAC-SHA256 |
| `HybridScheme` | core | AES-256-GCM for data > 65 bytes |
| `WitnessService` | witness | storeData(), reconstruct(), verifyDrop() |
| `DropService` | storage | Store/retrieve drops, TTL reaper |
| `ReplicationService` | storage | Peer-to-peer replication |

## Known Bugs (DO NOT reintroduce)

| Bug | Status | Description |
|-----|--------|-------------|
| BUG-001 | FIXED | `RainMap.create()` was `k=n` — now accepts k param |
| BUG-002 | FIXED | `ApiKeyFilter` used `String.equals()` — now `MessageDigest.isEqual()` |
| BUG-003 | FIXED | `ReplicationService` had no timeouts — now 5s + bounded pool |
| BUG-004 | OPEN | RateLimitConfig memory leak (bucket map never cleaned) |
| BUG-005 | OPEN | `getRainMap()` returns manual JSON without escaping |
| BUG-006 | FIXED | Debug logging exposed masterKey — now sanitized |
| BUG-007 | OPEN | `deleteByTtlBefore()` lacks explicit @Transactional |
| BUG-008 | OPEN | `StorageHealthIndicator` doesn't cover H2 data directory |

## Build Commands

```bash
# Compile core module
cd raindrops-fase1/raindrops && mvn compile -q

# Compile storage module
cd raindrops-fase1/storage && mvn compile -q

# Compile witness module
cd raindrops-fase1/witness && mvn compile -q

# Install core to local repo (required before compiling storage/witness)
cd raindrops-fase1/raindrops && mvn install -q -DskipTests

# Run tests
cd raindrops-fase1/raindrops && mvn test -q

# Full rebuild Docker
cd raindrops-fase1 && docker compose build --no-cache

# Restart containers
cd raindrops-fase1 && docker compose up -d --force-recreate

# Check container status
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep raindrops
```

## Code Conventions

- Java 21+ features (pattern matching, records, text blocks)
- SLF4J logging (no System.out.println)
- Javadoc on all public methods
- No comments unless asked (code should be self-documenting)
- HexFormat for hex encoding (not manual)
- Bouncy Castle for crypto (not javax.crypto)
- H2 embedded database (file-based for persistence)
- Spring Boot 3.x with Jakarta namespace

## Testing Patterns

```bash
# Quick end-to-end test
python3 /tmp/test_fix.py

# Manual curl test
curl -X POST http://172.17.0.1:9080/witness/store \
  -H "Content-Type: application/json" \
  -d '{"data":"<base64>","n":3,"k":2,"ttlDays":30}'
```

## When to use me

Use this skill when working on Rain Drops code — adding features, fixing bugs, or understanding the architecture. Always check known bugs before modifying code.
