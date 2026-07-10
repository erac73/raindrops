# API Reference — Rain Drops

Complete API documentation for all Rain Drops modules.

**Versions:** raindrops-core `0.1.0-SNAPSHOT`, raindrops-storage `0.1.0-SNAPSHOT`, raindrops-witness `0.1.0-SNAPSHOT`

---

## Table of Contents

1. [Core Library API](#1-core-library-api)
2. [Storage Node REST API](#2-storage-node-rest-api)
3. [Witness Node REST API](#3-witness-node-rest-api)
4. [RainClient SDK](#4-rainclient-sdk)
5. [Common Data Formats](#5-common-data-formats)

---

## 1. Core Library API

**Package:** `io.raindrops.core`

### 1.1 RainDropsCore

Main facade for DROP and RECONSTRUCT operations.

#### `RainDropsCore.drop(byte[] data, int n, int k, int ttlDays)`

Fragment data into N cryptographic drops.

| Parameter | Type | Description |
|---|---|---|
| `data` | `byte[]` | Original data to fragment |
| `n` | `int` | Total number of drops to create (2 ≤ n ≤ 255) |
| `k` | `int` | Threshold: minimum drops required (2 ≤ k ≤ n) |
| `ttlDays` | `int` | Time-to-live in days (> 0) |

**Returns:** `RainResult` — immutable result container.

**RainResult fields:**

| Field | Type | Description |
|---|---|---|
| `getDrops()` | `List<Drop>` | The N generated drops |
| `getMasterKey()` | `byte[]` | 32-byte master key (clone) |
| `getCiphertext()` | `byte[]` | AES-GCM ciphertext, or `null` in direct mode |
| `getN()` | `int` | N parameter |
| `getK()` | `int` | K parameter |
| `isDirectMode()` | `boolean` | True if data was ≤ 65 bytes |

**Throws:** `IllegalArgumentException` if parameters are invalid.

**Example:**

```java
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.Drop;
import io.raindrops.core.RainDropsCore.RainResult;

byte[] data = "Hello World!".getBytes();
RainResult result = RainDropsCore.drop(data, 5, 3, 30);

List<Drop> drops = result.getDrops();        // 5 drops
byte[] masterKey = result.getMasterKey();     // 32 bytes
byte[] ciphertext = result.getCiphertext();   // null (direct mode)
boolean direct = result.isDirectMode();       // true
```

#### `RainDropsCore.reconstruct(List<Drop> drops, byte[] masterKey, byte[] ciphertext, int k, boolean directMode)`

Reconstruct original data from K verified drops.

| Parameter | Type | Description |
|---|---|---|
| `drops` | `List<Drop>` | List of verified drops (must contain at least K valid) |
| `masterKey` | `byte[]` | 32-byte master key used during DROP |
| `ciphertext` | `byte[]` | Ciphertext from hybrid mode, or `null` for direct mode |
| `k` | `int` | Threshold parameter |
| `directMode` | `boolean` | True if reconstructing from direct mode |

**Returns:** `byte[]` — the original data.

**Throws:**
- `QuorumException` — if fewer than K drops provided
- `DropFactory.InvalidDropException` — if any drop fails verification
- `HybridScheme.CryptoException` — if AES-GCM decryption fails

**Example:**

```java
List<Drop> validDrops = drops.subList(0, 3); // K=3
byte[] original = RainDropsCore.reconstruct(
    validDrops, masterKey, ciphertext, 3, direct
);
System.out.println(new String(original)); // "Hello World!"
```

### 1.2 Drop

Cryptographic micro-unit data object.

**Fields (accessed via getters):**

| Method | Return | Description |
|---|---|---|
| `getId()` | `byte[]` | 32-byte opaque identifier |
| `getX()` | `int` | Share index (1-based) |
| `getY()` | `BigInteger` | Share value over GF(p) |
| `getMac()` | `byte[]` | 32-byte HMAC-SHA256 integrity tag |
| `getTtl()` | `long` | Unix epoch seconds of expiry |
| `isExpired()` | `boolean` | True if current time > TTL |
| `secondsUntilExpiry()` | `long` | Remaining seconds (negative if expired) |

### 1.3 DropFactory

#### `DropFactory.create(int x, BigInteger y, byte[] masterKey, int ttlDays)`

Create a drop with relative TTL.

#### `DropFactory.verifyOrThrow(Drop drop, byte[] masterKey)`

Verify drop integrity. Throws `InvalidDropException` on failure (expired, MAC mismatch).

#### `DropFactory.verify(Drop drop, byte[] masterKey)`

Returns `boolean` — true if drop is valid and not expired.

### 1.4 DropSerializer

#### `DropSerializer.toJson(Drop drop)` → `String`

Serialize a single drop to JSON.

#### `DropSerializer.fromJson(String json)` → `Drop`

Deserialize a single drop from JSON.

#### `DropSerializer.toJson(List<Drop> drops)` → `String`

Serialize a list of drops to JSON.

#### `DropSerializer.fromJsonList(String json)` → `List<Drop>`

Deserialize a JSON array to a list of drops.

**JSON Format:**

```json
{
  "id": "a1b2c3...",
  "x": 1,
  "y": "0123456789abcdef...",
  "mac": "deadbeef...",
  "ttl": 1777777777
}
```

### 1.5 RainMap

#### `RainMap.create(List<Drop> drops, List<String> nodeUrls, byte[] masterKey)`

Create an encrypted index mapping drop IDs to storage URLs.

#### `RainMap.fromEncrypted(byte[] combinedPayload, byte[] masterKey)`

Reconstruct a RainMap from its encrypted payload.

#### `RainMap.unseal(byte[] masterKey)` → `Map<String, String>`

Decrypt and return the URL index: `Map<hexDropId, nodeUrl>`.

#### `RainMap.getCombinedPayload()` → `byte[]`

Return nonce + ciphertext combined for external storage.

### 1.6 ShamirSSS

#### `ShamirSSS.split(BigInteger secret, int n, int k)` → `List<BigInteger[]>`

Split secret into N shares. Each share is `[x, y]`.

#### `ShamirSSS.combine(List<BigInteger[]> shares)` → `BigInteger`

Reconstruct secret from K shares using Lagrange interpolation.

#### `ShamirSSS.bytesToSecret(byte[] bytes)` → `BigInteger`

Convert bytes to BigInteger (< prime).

#### `ShamirSSS.secretToBytes(BigInteger value, int length)` → `byte[]`

Convert BigInteger back to fixed-length byte array.

### 1.7 HybridScheme

#### `HybridScheme.encrypt(byte[] plaintext)` → `EncryptionResult`

Encrypt data with AES-256-GCM. Returns key + ciphertext.

#### `HybridScheme.decrypt(byte[] aesKey, byte[] ciphertext)` → `byte[]`

Decrypt ciphertext with AES key.

---

## 2. Storage Node REST API

**Base URL:** `http://<host>:<port>` (default port: 9081-9083, container port: 8080)

### 2.1 Health Check

```
GET /health
```

**Response 200:**

```json
{
  "status": "UP",
  "nodeId": "keeper-1",
  "dropsStored": 42,
  "peers": ["http://keeper-2:8080", "http://keeper-3:8080"]
}
```

### 2.2 Store a Drop

```
POST /drops
Content-Type: application/json
```

**Request body:** Drop JSON (see DropSerializer format).

**Response 200:**

```json
{
  "dropId": "a1b2c3d4e5f6...",
  "nodeId": "keeper-1"
}
```

The drop is replicated asynchronously to all peers.

### 2.3 Retrieve a Drop

```
GET /drops/{dropId}
```

**Response 200:** Drop JSON.

**Response 404:** Drop not found locally or on any peer.

### 2.4 Build and Store RainMap

```
POST /rainmaps
Content-Type: application/json
```

**Request body:**

```json
{
  "drops": ["<drop-json-0>", "<drop-json-1>", ...],
  "nodeUrls": ["http://keeper-1:8080", "http://keeper-2:8080", ...],
  "masterKeyHex": "aabbccdd..."
}
```

**Response 200:**

```json
{
  "rainMapId": "a1b2c3d4...",
  "encryptedPayloadHex": "eeff0011...",
  "n": 5,
  "k": 3
}
```

### 2.5 Store External RainMap (replication)

```
POST /rainmaps/external
Content-Type: application/json
```

**Request body:**

```json
{
  "rainMapId": "a1b2c3d4...",
  "encryptedPayloadHex": "eeff0011...",
  "n": 5,
  "k": 3
}
```

**Response 200:**

```json
{
  "rainMapId": "a1b2c3d4..."
}
```

### 2.6 Retrieve RainMap

```
GET /rainmaps/{rainMapId}
```

**Response 200:**

```json
{
  "rainMapId": "a1b2c3d4...",
  "encryptedPayloadHex": "eeff0011...",
  "n": 5,
  "k": 3,
  "directMode": false,
  "ciphertextHex": "11223344...",
  "nodeId": "keeper-1"
}
```

`directMode` is `true` when no ciphertext is stored (data ≤ 65 bytes).

**Response 404:** RainMap not found.

### 2.7 Update Ciphertext

```
PUT /rainmaps/{rainMapId}/ciphertext
Content-Type: application/json
```

**Request body:**

```json
{
  "ciphertextHex": "1122334455..."
}
```

**Response 200:**

```json
{
  "rainMapId": "a1b2c3d4..."
}
```

### 2.8 List Peers

```
GET /peers
```

**Response 200:**

```json
{
  "nodeId": "keeper-1",
  "peers": ["http://keeper-2:8080", "http://keeper-3:8080"]
}
```

### 2.9 Dashboard

```
GET /
```

Renders an HTML dashboard (Thymeleaf) with node status, drop count, peers.

### 2.10 Swagger UI

```
GET /swagger-ui.html
```

Interactive API documentation.

### 2.11 OpenAPI Spec

```
GET /api-docs
```

OpenAPI 3.0 JSON specification.

### 2.12 Prometheus Metrics

```
GET /actuator/prometheus
```

Prometheus-format metrics.

### 2.13 Actuator Endpoints

```
GET /actuator/health
GET /actuator/info
```

---

## 3. Witness Node REST API

**Base URL:** `http://<host>:<port>` (default: 9080, container: 8080)

### 3.1 Store Data

```
POST /witness/store
Content-Type: application/json
```

**Request body:**

```json
{
  "data": "<base64-encoded-data>",
  "n": 5,
  "k": 3,
  "ttlDays": 30
}
```

| Field | Type | Description |
|---|---|---|
| `data` | `string` | Base64-encoded data to store |
| `n` | `number` | Total drops (2-255) |
| `k` | `number` | Threshold (2-n) |
| `ttlDays` | `number` | Time-to-live in days |

**Response 200:**

```json
{
  "rainMapId": "a1b2c3d4e5f6...",
  "masterKeyHex": "aabbccdd..."
}
```

**Response 400:**

```json
{
  "error": "Store failed"
}
```

**curl example:**

```bash
DATA_B64=$(echo -n "Hello Rain Drops!" | base64)
curl -s -X POST http://localhost:9080/witness/store \
  -H "Content-Type: application/json" \
  -d "{\"data\": \"$DATA_B64\", \"n\": 5, \"k\": 3, \"ttlDays\": 30}"
```

### 3.2 Reconstruct Data

```
POST /witness/reconstruct
Content-Type: application/json
```

**Request body:**

```json
{
  "rainMapId": "a1b2c3d4e5f6...",
  "masterKeyHex": "aabbccdd..."
}
```

**Response 200 (success):**

```json
{
  "success": true,
  "data": "<base64-decoded-data>",
  "badDrops": [],
  "n": 5,
  "k": 3
}
```

**Response 200 (failure):**

```json
{
  "success": false,
  "message": "Quorum insufficient: 0/3 valid drops",
  "badDrops": ["abc: fetch error - Connection refused"],
  "n": 5,
  "k": 3
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:9080/witness/reconstruct \
  -H "Content-Type: application/json" \
  -d '{"rainMapId": "<id>", "masterKeyHex": "<key>"}'
```

### 3.3 Verify a Drop

```
POST /witness/verify
Content-Type: application/json
```

**Request body:**

```json
{
  "dropJson": "<full-drop-json>",
  "masterKeyHex": "aabbccdd..."
}
```

**Response 200:**

```json
{
  "valid": true,
  "message": "Drop valid"
}
```

```json
{
  "valid": false,
  "message": "Drop expired"
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:9080/witness/verify \
  -H "Content-Type: application/json" \
  -d '{"dropJson": "{\"id\":\"...\",\"x\":1,\"y\":\"...\",\"mac\":\"...\",\"ttl\":1777777777}", "masterKeyHex": "<key>"}'
```

### 3.4 Health Check

```
GET /health
```

**Response 200:**

```json
{
  "status": "UP",
  "service": "witness"
}
```

---

## 4. RainClient SDK

**Package:** `io.raindrops.client`  
**Class:** `RainClient implements AutoCloseable`

### 4.1 Constructor

```java
RainClient(List<String> nodeUrls)
```

| Parameter | Description |
|---|---|
| `nodeUrls` | List of storage node base URLs (e.g., `["http://localhost:9081", "http://localhost:9082", "http://localhost:9083"]`) |

Connection timeout: 10 seconds. Per-request timeout: 15 seconds.

### 4.2 store

```java
String store(byte[] data, int n, int k, int ttlDays)
```

Full DROP pipeline over HTTP. Returns `rainMapId`.

| Parameter | Description |
|---|---|
| `data` | Data to fragment |
| `n` | Total drops |
| `k` | Threshold |
| `ttlDays` | TTL in days |

**Flow:**
1. Calls `RainDropsCore.drop()` locally
2. POSTs each drop to a storage node (round-robin)
3. Creates and stores RainMap on first node
4. Stores ciphertext (hybrid mode) on first node

### 4.3 retrieve

```java
byte[] retrieve(String rainMapId, byte[] masterKey)
```

Full RECONSTRUCT pipeline over HTTP. Returns original data.

| Parameter | Description |
|---|---|
| `rainMapId` | ID returned from `store()` |
| `masterKey` | 32-byte master key |

**Flow:**
1. GETs RainMap from first storage node
2. Decrypts RainMap to get drop-to-node mapping
3. GETs drops from their respective nodes
4. Calls `RainDropsCore.reconstruct()` locally

### 4.4 close

```java
void close()
```

Cleanup resources (currently no-op, included for `try-with-resources` compatibility).

### 4.5 Usage Example

```java
import io.raindrops.client.RainClient;
import java.util.List;

List<String> nodes = List.of(
    "http://localhost:9081",
    "http://localhost:9082",
    "http://localhost:9083"
);

try (RainClient client = new RainClient(nodes)) {
    // Store
    String rainMapId = client.store(
        "Confidential document".getBytes(),
        5,     // N = 5 drops
        3,     // K = 3 threshold
        30     // 30-day TTL
    );

    // Note: masterKey is needed for retrieval
    // In production, return it alongside rainMapId
    System.out.println("Stored as: " + rainMapId);

    // Retrieve (requires masterKey from store)
    // byte[] data = client.retrieve(rainMapId, masterKey);
    // System.out.println(new String(data));
}
```

### 4.6 Maven Dependency

```xml
<dependency>
    <groupId>io.raindrops</groupId>
    <artifactId>raindrops-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

---

## 5. Common Data Formats

### 5.1 Drop JSON

```json
{
  "id": "a1b2c3d4e5f6...",
  "x": 1,
  "y": "00a1b2c3d4e5f6...",
  "mac": "deadbeef...",
  "ttl": 1777777777
}
```

| Field | Type | Encoding |
|---|---|---|
| `id` | `string` | Hex (64 hex chars for 32 bytes) |
| `x` | `number` | Integer (1-based index) |
| `y` | `string` | Hex (BigInteger value, variable length, typically 132 hex chars) |
| `mac` | `string` | Hex (64 hex chars for 32 bytes) |
| `ttl` | `number` | Unix epoch seconds |

### 5.2 RainMap JSON (from storage)

```json
{
  "rainMapId": "a1b2c3d4e5f6...",
  "encryptedPayloadHex": "eeff00112233...",
  "n": 5,
  "k": 3,
  "directMode": false,
  "ciphertextHex": "11223344...",
  "nodeId": "keeper-1"
}
```

| Field | Type | Description |
|---|---|---|
| `rainMapId` | `string` | Hex (sha256 of first drop ID) |
| `encryptedPayloadHex` | `string` | Hex (nonce(12) + AES-GCM ciphertext) |
| `n` | `number` | Total drops |
| `k` | `number` | Threshold |
| `directMode` | `boolean` | True if no ciphertext stored |
| `ciphertextHex` | `string` or `null` | Hex of ciphertext (null in direct mode) |
| `nodeId` | `string` | ID of the node storing this RainMap |

### 5.3 Authentication

When `API_KEY` is configured, all storage node endpoints (except public ones) require:

```
X-API-Key: <api-key-value>
```

Public endpoints (no auth required):
- `GET /`
- `GET /health`
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /swagger-ui/**`
- `GET /api-docs/**`
- `GET /h2-console/**`

---

*Rain Drops — API Reference v0.1.0 — Edwar Antonio Ramírez Castillo, 2026*
