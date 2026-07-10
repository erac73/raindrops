# Arquitectura de Rain Drops

> Documento técnico de arquitectura del modelo de almacenamiento distribuido basado en criptografía de umbral.

**Versión:** 0.1.0 | **Fase:** 3 — Nodo Testigo | **Autor:** Edwar Antonio Ramírez Castillo

---

## Índice

1. [Visión General](#1-visión-general)
2. [Módulos del Sistema](#2-módulos-del-sistema)
3. [Arquitectura Criptográfica](#3-arquitectura-criptográfica)
4. [Flujo de Datos](#4-flujo-de-datos)
5. [Arquitectura de Red](#5-arquitectura-de-red)
6. [Modelo de Seguridad](#6-modelo-de-seguridad)
7. [Despliegue](#7-despliegue)
8. [Referencia de Configuración](#8-referencia-de-configuración)

---

## 1. Visión General

Rain Drops implementa un modelo de almacenamiento distribuido donde la confidencialidad es una propiedad estructural del propio modelo, no una capa añadida. El sistema fragmenta datos mediante **Shamir's Secret Sharing (SSS)** en unidades criptográficas llamadas *gotas (drops)* que, individualmente, son indistinguibles de ruido aleatorio.

### 1.1 Principios de Diseño

| Principio | Descripción |
|---|---|
| **Secreto Perfecto** | Con K-1 gotas, la información del secreto es cero — garantía information-theoretic, no computacional |
| **Sin Confianza Central** | Ningún nodo posee el dato completo ni la clave maestra |
| **Verificación Independiente** | Cada gota lleva su propio MAC; cualquier parte puede verificar integridad |
| **Expiración Temporal** | Las gotas expiran; el acceso al dato tiene caducidad inherente |
| **Composibilidad** | Los módulos (core, storage, witness) son independientes y desplegables por separado |

### 1.2 Metáfora Conceptual

> *Una gota de lluvia no contiene información sobre la tormenta.*

Así como una sola gota no revela la estructura de una tormenta, un solo *drop* criptográfico no revela nada sobre el dato original. Solo cuando K de N drops convergen — bajo verificación de integridad — el dato emerge.

---

## 2. Módulos del Sistema

### 2.1 Diagrama de Módulos

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
│  │  │  (índice cifr.)│  │  (SDK HTTP)  │                     │  │
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
│  │  │ RainMapService         │  │  │  │ (orquestador)      │ │ │
│  │  │ ReplicationService     │  │  │  └────────────────────┘ │ │
│  │  └────────────────────────┘  │  └──────────────────────────┘ │
│  └──────────────────────────────┘                               │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 raindrops-core

**Propósito:** Biblioteca criptográfica fundamental. No depende de Spring Boot; es un JAR autónomo.

| Clase | Responsabilidad |
|---|---|
| `ShamirSSS` | Implementación de Shamir's Secret Sharing sobre GF(2⁵²¹ − 1) |
| `HybridScheme` | Cifrado AES-256-GCM para modo híbrido |
| `Drop` | Modelo de datos de una gota: `(id, x, y, mac, ttl)` |
| `DropFactory` | Creación y verificación de gotas con HMAC-SHA256 |
| `DropSerializer` | Serialización/deserialización JSON de gotas |
| `RainDropsCore` | Fachada principal: operaciones `drop()` y `reconstruct()` |
| `RainMap` | Índice cifrado que mapea drop IDs a URLs de nodos |
| `RainClient` | SDK cliente HTTP para operaciones contra nodos remotos |

**Dependencias:** Bouncy Castle (`bcprov-jdk18on`), Jackson (`jackson-databind`)

### 2.3 raindrops-storage

**Propósito:** Nodo de almacenamiento Spring Boot que persiste gotas y RainMaps.

| Componente | Tecnología |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Base de datos | H2 embebida (archivo) |
| ORM | Spring Data JPA / Hibernate |
| API | REST (Spring Web) |
| Documentación API | Springdoc OpenAPI / Swagger UI |
| Métricas | Prometheus + Micrometer |
| Templating | Thymeleaf (Dashboard) |
| Seguridad | Spring Security + API Key filter |

**Endpoints REST:**

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/drops` | Almacenar una gota |
| GET | `/drops/{dropId}` | Recuperar una gota |
| POST | `/rainmaps` | Construir y almacenar RainMap |
| POST | `/rainmaps/external` | Almacenar RainMap replicado |
| GET | `/rainmaps/{rainMapId}` | Recuperar RainMap |
| PUT | `/rainmaps/{rainMapId}/ciphertext` | Actualizar ciphertext |
| GET | `/peers` | Listar pares conocidos |
| GET | `/health` | Health check |

**Replicación:** Asíncrona (fire-and-forget) a todos los pares configurados vía `PEER_URLS`.

### 2.4 raindrops-witness

**Propósito:** Nodo testigo (witness) sin estado que orquesta y verifica operaciones.

| Componente | Tecnología |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Comunicación | `RestClient` (Spring 6) |
| API | REST (Spring Web) |

**Endpoints REST:**

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/witness/store` | Fragmentar, distribuir y almacenar |
| POST | `/witness/reconstruct` | Recolectar, verificar y reconstruir |
| POST | `/witness/verify` | Verificar integridad de una gota |
| GET | `/health` | Health check |

---

## 3. Arquitectura Criptográfica

### 3.1 Shamir's Secret Sharing (SSS)

**Parámetros:**
- **Primo:** `p = 2⁵²¹ − 1` (Mersenne prime, 521 bits)
- **Cuerpo finito:** GF(p)
- **Secretos máximo:** 65 bytes (`⌊521/8⌋`)

**Algoritmo de Partición (`split`):**
1. Generar polinomio aleatorio de grado `k−1`: `f(x) = s + a₁x + a₂x² + ... + aₖ₋₁xᵏ⁻¹`
   - `s` = secreto (BigInteger < p)
   - `aᵢ` = coeficientes aleatorios uniformes en GF(p)
2. Evaluar `f(x)` en `x = 1, 2, ..., n` usando **método de Horner** (O(k) por evaluación)
3. Retornar `n` pares `(x, f(x))` — las *shares*

**Algoritmo de Reconstrucción (`combine`):**
1. Tomar `k` shares `(xᵢ, yᵢ)`
2. Interpolación de Lagrange: `s = Σᵢ yᵢ · Lᵢ(0) mod p`
   - `Lᵢ(0) = Πⱼ≠ᵢ (−xⱼ) · (xᵢ − xⱼ)⁻¹ mod p`
3. Retornar `s`

**Seguridad:** Con menos de `k` shares, la distribución de `s` es uniforme sobre GF(p) — secreto perfecto (Demostración: Teorema 1 de Shamir, 1979).

### 3.2 Esquema Híbrido (HybridScheme)

**Modo Directo** (datos ≤ 65 bytes):
- El secreto SSS es el propio dato (padding a 65 bytes)
- No se genera ciphertext
- `directMode = true`

**Modo Híbrido** (datos > 65 bytes):
1. Generar clave AES-256 aleatoria (32 bytes)
2. Cifrar datos con AES-256-GCM → `nonce(12) || ciphertext || tag(16)`
3. El secreto SSS es la clave AES (32 bytes)
4. El ciphertext se almacena por separado
5. `directMode = false`

**Cifrado AES-256-GCM:**
- Implementación: Bouncy Castle `GCMBlockCipher(AESEngine)`
- Nonce: 12 bytes (recomendación NIST para GCM)
- Tag: 128 bits
- Seguridad: IND-CCA2 (Integrated Encryption Scheme)

### 3.3 Gota (Drop)

**Estructura:** `d = (id, x, y, mac, ttl)`

| Campo | Tamaño | Descripción |
|---|---|---|
| `id` | 32 bytes | `HMAC-SHA256(nonce, masterKey)` — ID opaco, impredecible |
| `x` | 4 bytes | Coordenada SSS (índice 1-based, entero) |
| `y` | ~66 bytes | Valor de share: `f(x) mod p` (BigInteger) |
| `mac` | 32 bytes | `HMAC-SHA256(x ‖ y ‖ ttl, masterKey)` |
| `ttl` | 8 bytes | Unix timestamp de expiración |

**Propiedades de Seguridad:**
- Una gota aislada es indistinguible de ruido aleatorio
- El ID no revela relación con otras gotas del mismo conjunto
- El MAC vincula la gota a su clave maestra y TTL
- Las gotas expiradas son irrecoverables

### 3.4 RainMap

**Propósito:** Índice cifrado que mapea `dropID → storageNodeURL`.

**Ciclo de Vida:**
1. **Creación:** `RainMap.create(drops, urls, masterKey)` serializa el índice como JSON, lo cifra con AES-256-GCM usando `masterKey`, almacena nonce + ciphertext combinados
2. **Almacenamiento:** El payload combinado se envía al primer nodo de almacenamiento como `encryptedPayloadHex`
3. **Recuperación:** `RainMap.fromEncrypted(payload, masterKey)` descifra y reconstruye el índice
4. **Uso:** `rainMap.unseal(masterKey)` retorna `Map<dropId, nodeUrl>` para localizar gotas

**Formato del Payload Cifrado:**
```
[nonce (12 bytes)] [ciphertext AES-GCM]
```
Donde `ciphertext` descifra a:
```json
{
  "urlIndex": { "abc123...": "http://keeper-1:8080", ... },
  "n": 5,
  "k": 3
}
```

---

## 4. Flujo de Datos

### 4.1 Almacenamiento (STORE)

```
Cliente                    Witness                         Storage Nodes
  │                          │                                  │
  │  POST /witness/store     │                                  │
  │  {data, n, k, ttlDays}   │                                  │
  │ ──────────────────────►  │                                  │
  │                          │  RainDropsCore.drop(data,n,k)    │
  │                          │  ──► N Drops + masterKey         │
  │                          │       + ciphertext (opcional)    │
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
  │                          │  (solo modo híbrido)             │
  │                          │                                  │
  │  {rainMapId, masterKey}  │                                  │
  │ ◄──────────────────────  │                                  │
```

**Distribución Round-Robin:**
Los `n` drops se distribuyen entre los nodos de almacenamiento en orden cíclico. Para `N=5` y 3 nodos: drop[0]→keeper-1, drop[1]→keeper-2, drop[2]→keeper-3, drop[3]→keeper-1, drop[4]→keeper-2.

### 4.2 Reconstrucción (RECONSTRUCT)

```
Cliente                    Witness                         Storage Nodes
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
  │                          │  ... (hasta K gotas válidas)    │
  │                          │                                  │
  │                          │  DropFactory.verifyOrThrow()    │
  │                          │  (MAC + TTL) para cada gota      │
  │                          │                                  │
  │                          │  RainDropsCore.reconstruct()     │
  │                          │  ──► dato original               │
  │                          │                                  │
  │  {success, data(base64)} │                                  │
  │ ◄──────────────────────  │                                  │
```

**Optimización:** El witness deja de recolectar gotas en cuanto tiene `k` válidas. Si no alcanza el quorum, retorna error con lista de gotas inválidas.

### 4.3 Verificación (VERIFY)

```
Cliente                    Witness
  │                          │
  │  POST /witness/verify    │
  │  {dropJson, masterKeyHex}│
  │ ──────────────────────►  │
  │                          │  1. Deserializar dropJson
  │                          │  2. drop.isExpired()?
  │                          │  3. DropFactory.verifyOrThrow()
  │                          │     ─► HMAC-SHA256(x‖y‖ttl, mk)
  │                          │     ─► comparación en tiempo cte
  │  {valid, message}        │
  │ ◄──────────────────────  │
```

### 4.4 Replicación entre Nodos de Almacenamiento

```
Storage Node A                    Storage Node B
  │                                    │
  │  (local store)                      │
  │  POST /drops {body}                 │
  │  ──► guardar en H2                  │
  │  ──► ReplicationService             │
  │       └─► async POST /drops ───────►│ B almacena copia
  │       └─► async POST /drops ───────►│ C almacena copia
  │                                    │
  │  Lectura local falla:              │
  │  GET /drops/{id} (404)             │
  │  ──► ReplicationService            │
  │       └─► sync GET /drops/{id} ───►│ B responde (o C)
  │                                    │
```

---

## 5. Arquitectura de Red

### 5.1 Topología

```
                  ┌──────────────┐
                  │   Cliente    │
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
                   Red Docker (rain-net)
```

### 5.2 Puertos

| Servicio | Puerto Contenedor | Puerto Host | Propósito |
|---|---|---|---|
| Witness Node | 8080 | 9080 | API REST de coordinación |
| Storage Node 1 | 8080 | 9081 | API REST de almacenamiento |
| Storage Node 2 | 8080 | 9082 | API REST de almacenamiento |
| Storage Node 3 | 8080 | 9083 | API REST de almacenamiento |

### 5.3 Red Docker

Todos los servicios comparten la red `rain-net` (bridge). Los nombres DNS internos son:
- `witness` → Witness Node
- `keeper-1` → Storage Node 1
- `keeper-2` → Storage Node 2
- `keeper-3` → Storage Node 3

---

## 6. Modelo de Seguridad

### 6.1 Capas de Seguridad

```
┌────────────────────────────────────────────────────┐
│                   Capa de Aplicación                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ API Key Auth │  │    TLS      │  │ Rate Limit│ │
│  │ (X-API-Key)  │  │ (opcional)  │  │ (futuro)  │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
├────────────────────────────────────────────────────┤
│                Capa Criptográfica                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │  Shamir SSS  │  │  AES-256-GCM │  │HMAC-SHA256│ │
│  │  GF(p) K-of-N│  │  modo híbrido│  │integridad │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
├────────────────────────────────────────────────────┤
│              Capa de Infraestructura                │
│  ┌──────────────┐  ┌──────────────┐               │
│  │ redes Docker │  │  volúmenes   │               │
│  │  aisladas    │  │  persist.    │               │
│  └──────────────┘  └──────────────┘               │
└────────────────────────────────────────────────────┘
```

### 6.2 Autenticación (API Key)

- Activada mediante variable de entorno `API_KEY`
- Cabecera HTTP: `X-API-Key: <valor>`
- Endpoints públicos (sin autenticación): `/health`, `/`, `/actuator/*`, `/swagger-ui/**`, `/api-docs/**`, `/h2-console/**`
- Si `API_KEY` está vacío: modo abierto (todos los endpoints accesibles)

### 6.3 TLS

- Desactivado por defecto
- Activar con `TLS_ENABLED=true`
- Requiere keystore JKS montado en el contenedor
- Configuración: `server.ssl.key-store`, `server.ssl.key-store-password`

### 6.4 Expiración Temporal (TTL)

- Cada gota tiene un TTL en segundos Unix
- Un proceso *reaper* ejecuta cada 60 segundos eliminando gotas expiradas
- Gotas expiradas son irrecoverables — ni siquiera con la clave maestra

---

## 7. Despliegue

### 7.1 Docker Compose (Desarrollo/Local)

```bash
# Construir y levantar
docker compose -f docker-compose.yml up -d --build

# Verificar estado
docker compose -f docker-compose.yml ps

# Logs
docker compose -f docker-compose.yml logs -f

# Sin autenticación
API_KEY="" docker compose -f docker-compose.yml up -d --build

# Con autenticación
API_KEY="mi-clave-secreta" docker compose -f docker-compose.yml up -d --build
```

### 7.2 Raspberry Pi (ARM64)

```bash
# Clonar en la Pi
git clone https://github.com/erac73/raindrops-fase1.git
cd raindrops-fase1

# Desplegar
docker compose -f docker-compose.yml up -d --build
```

Las imágenes Docker soportan `linux/arm64` nativamente (construidas con QEMU + Buildx multi-arch).

### 7.3 Despliegue Automatizado

**GitHub Actions:** El workflow `.github/workflows/deploy.yml` permite despliegue manual via `workflow_dispatch` con SSH.

**Script Paramiko:** `deploy_pi.py` automatiza clone/pull + build + deploy en Raspberry Pi.

### 7.4 Variables de Entorno

| Variable | Default | Servicio | Descripción |
|---|---|---|---|
| `API_KEY` | `""` | Storage, Witness | Clave para autenticación API |
| `NODE_ID` | `"storage-node"` | Storage | Identificador del nodo |
| `PEER_URLS` | `""` | Storage | URLs de pares separadas por coma |
| `STORAGE_URLS` | `""` | Witness | URLs de nodos de almacenamiento |
| `WITNESS_PORT` | `8080` | Witness | Puerto del servidor witness |
| `STORAGE_PORT` | `8080` | Storage | Puerto del servidor storage |
| `TLS_ENABLED` | `"false"` | Storage | Habilitar TLS |
| `TLS_KEYSTORE` | `""` | Storage | Ruta al keystore JKS |

---

## 8. Referencia de Configuración

### 8.1 Estructura de Archivos

```
raindrops-fase1/
├── raindrops/                      ← Módulo core
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/raindrops/
│       │   ├── core/              ← SSS, AES-GCM, Drop, RainMap
│       │   └── client/            ← RainClient SDK
│       └── test/java/io/raindrops/core/
├── storage/                        ← Módulo de almacenamiento
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/raindrops/storage/
│       │   ├── config/            ← Security, Reaper, Peer config
│       │   ├── controller/        ← REST API
│       │   ├── service/           ← Lógica de negocio
│       │   ├── model/             ← Entidades JPA
│       │   └── repository/        ← Acceso a datos
│       └── resources/
│           ├── application.yml
│           └── templates/dashboard.html
├── witness/                        ← Módulo testigo
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/raindrops/witness/
│       │   ├── controller/        ← REST API
│       │   └── service/           ← Orquestación
│       └── resources/application.yml
├── docs/                           ← Documentación y demo web
├── .github/workflows/              ← CI/CD
├── Dockerfile.storage              ← Build multi-arch storage
├── Dockerfile.witness              ← Build multi-arch witness
├── docker-compose.yml              ← Orquestación
├── entrypoint.sh                   ← Entrypoint contenedores
└── prometheus.yml                  ← Config Prometheus
```

### 8.2 Compilación

```bash
# Compilar todo el core y ejecutar tests
mvn -f raindrops-fase1/raindrops/pom.xml install

# Compilar storage (requiere core instalado)
mvn -f raindrops-fase1/storage/pom.xml package -DskipTests

# Compilar witness (requiere core instalado)
mvn -f raindrops-fase1/witness/pom.xml package -DskipTests

# Ejecutar tests de integración
mvn -f raindrops-fase1/storage/pom.xml test
```

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026. Documento de arquitectura versión 0.1.0.*
