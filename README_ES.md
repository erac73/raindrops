<p align="center">
  <img src="assets/raindrops-logo.svg" alt="Rain Drops Logo" width="200"/>
</p>

<h1 align="center">Rain Drops</h1>

<p align="center">
  <em>"Una gota de lluvia no contiene información sobre la tormenta."</em>
</p>

<p align="center">
  <strong>Autor:</strong> Edwar Antonio Ramírez Castillo &nbsp;|&nbsp;
  <strong>Estado:</strong> Fase 4 — Producción <img src="https://img.shields.io/badge/status-beta-blue?style=flat-square" alt="Beta"/>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/English-README-blue?style=flat-square" alt="English"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"/></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=flat-square" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Docker-multi--arch-blue?style=flat-square" alt="Docker multi-arch"/>
  <img src="https://img.shields.io/badge/Phase-4%20Production-purple?style=flat-square" alt="Phase 4"/>
</p>

---

## ¿Qué es Rain Drops?

**Rain Drops** es un modelo de almacenamiento distribuido de información basado en **criptografía de umbral**. Los datos se fragmentan en micro-unidades criptográficas llamadas **gotas (drops)** que, individualmente, son indistinguibles de ruido aleatorio. Los datos originales solo pueden reconstruirse cuando un número suficiente de gotas convergen bajo condiciones verificadas.

La metáfora es precisa: al igual que una gota de lluvia no contiene información sobre la tormenta, cada drop es, aislado, ruido puro. Solo cuando **K de N gotas** se combinan, los datos emergen.

> **Esto no es cifrado encima de almacenamiento. La confidencialidad es una propiedad estructural del modelo.**

---

## ¿Por qué Rain Drops?

### El problema con el almacenamiento tradicional

| Problema | Solución tradicional | Limitación |
|---|---|---|
| Datos en claro en servidores | Cifrado de disco | Un atacante con acceso root tiene las claves |
| Backup centralizado | Réplicas | Un solo punto de fallo o confiscación |
| Acceso multi-usuario | ACLs / RBAC | El administrador del sistema puede acceder |
| Compliance (GDPR/HIPAA) | Políticas y procedimientos | La seguridad depende de que todos cumplan |
| Ransomware | Backups air-gapped | Costoso, complejo, no escalable |

### La solución Rain Drops

```
┌─────────────────────────────────────────────────────────────────┐
│  DATOS ORIGINALES                                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  "Mi secreto"                                            │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  AES-256-GCM (cifrado + integridad)                      │  │
│  │  → Ciphertext (ruido aleatorio)                          │  │
│  │  → Clave AES (secreto a fragmentar)                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Shamir SSS (K,N) — Umbral sobre GF(2^521-1)            │  │
│  │  → N gotas (drops) individualmente inútiles              │  │
│  │  → Solo K gotas pueden reconstruir la clave AES          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│              ┌───────────────┼───────────────┐                  │
│              ▼               ▼               ▼                  │
│         ┌─────────┐    ┌─────────┐    ┌─────────┐              │
│         │ Drop 1  │    │ Drop 2  │    │ Drop N  │              │
│         │ (ruido) │    │ (ruido) │    │ (ruido) │              │
│         └─────────┘    └─────────┘    └─────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

**Propiedades matemáticas garantizadas:**

- **Secreto perfecto**: Con menos de K drops, la distribución del secreto es uniforme sobre GF(p). No es "difícil de descifrar" — es **imposible** matemáticamente.
- **IND-CCA2**: Seguro contra ataques de texto cifrado elegido (máxima seguridad estándar).
- **Integridad estructural**: Cada drop lleva HMAC-SHA256. El Witness Node verifica todo antes de reconstruir.
- **Expiración temporal**: Los drops llevan TTL. Un drop expirado es irrecuperable.

---

## Arquitectura del Sistema

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CLIENTE                                         │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  RainClient SDK                                                 │  │
│  │  → store(data, N, K, ttlDays)  →  RainMapId + MasterKey        │  │
│  │  → retrieve(rainMapId, masterKey)  →  data                      │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                              │                                        │
│                              ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  Nodo Testigo (Witness Node)                                    │  │
│  │  → Verifica integridad de cada drop antes de reconstruir        │  │
│  │  → Coordina almacenamiento y reconstrucción                     │  │
│  │  → Stateless: no almacena datos, solo verifica                  │  │
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

### Flujo de Almacenamiento (STORE)

```
1. Cliente envía datos al Witness Node
2. Witness genera clave AES aleatoria → cifra datos → SSS sobre clave
3. Witness distribuye N drops a N nodos de almacenamiento
4. Witness crea RainMap (índice cifrado: drop_id → nodo_url)
5. Witness almacena RainMap en el nodo primario
6. Retorna: rainMapId + masterKey (al cliente)
```

### Flujo de Reconstrucción (RECONSTRUCT)

```
1. Cliente envía rainMapId + masterKey al Witness
2. Witness descifra RainMap → obtiene ubicación de cada drop
3. Witness recolecta drops de los nodos de almacenamiento
4. Witness VERIFICA cada drop (HMAC + TTL) ← CRÍTICO
5. Con K drops válidos → interpolación de Lagrange → clave AES
6. Witness descifra ciphertext con clave AES reconstruida
7. Retorna: datos originales al cliente
```

---

## Propiedades de Seguridad

| Propiedad | Garantía | Mecanismo |
|---|---|---|
| **Secreto perfecto** | Con K-1 drops: cero información | Shamir SSS sobre GF(p), p = 2^521-1 |
| **IND-CCA2** | Ciphertext indistinguible de ruido | AES-256-GCM + clave aleatoria de 256 bits |
| **Integridad** | Tampering detectado | HMAC-SHA256 en cada drop |
| **Autenticidad** | Drops verificados antes de reconstruct | Witness Node verifica MAC de cada drop |
| **Identidad ciega** | Sin correlación entre drops | IDs = HMAC(nonce, masterKey) |
| **Expiración** | Drops inutilizables después de TTL | TTL codificado en cada drop + Reaper automático |
| **Resiliencia** | Tolerancia a N-K nodos caídos | Drops distribuidos + replicación automática |
| **Zero-knowledge storage** | El storage node NUNCA ve los datos en claro | Solo almacena drops (ruido) |

---

## Componentes del Sistema

### raindrops-core (Núcleo Criptográfico)

| Clase | Responsabilidad |
|---|---|
| `ShamirSSS` | Implementación de Shamir's Secret Sharing sobre GF(2^521-1) |
| `HybridScheme` | Esquema híbrido AES-256-GCM + SSS para datos arbitrarios |
| `RainDropsCore` | Fachada principal: orquesta DROP y RECONSTRUCT |
| `Drop` | Modelo de gota: (id, x, y, mac, ttl) |
| `DropFactory` | Creación y verificación de drops |
| `RainMap` | Índice cifrado que mapea drop_ids a nodos de almacenamiento |
| `RainClient` | SDK cliente para operaciones DROP/RECONSTRUCT vía red |

### Storage Node (Nodo de Almacenamiento)

| Componente | Responsabilidad |
|---|---|
| `DropService` | CRUD de drops con TTL automático |
| `RainMapService` | Gestión de RainMaps con replicación |
| `ReplicationService` | Replicación de drops y RainMaps entre peers |
| `ReaperConfig` | Limpieza automática de drops expirados |
| `SecurityConfig` | Autenticación API Key + endpoints públicos |
| `DashboardController` | UI de monitoreo (Thymeleaf) |

### Witness Node (Nodo Testigo)

| Componente | Responsabilidad |
|---|---|
| `WitnessService` | Lógica de verificación, almacenamiento y reconstrucción |
| `WitnessController` | REST API: /witness/store, /witness/reconstruct, /witness/verify |

📖 **Documentación completa:** [Guía de Arquitectura](docs/ARCHITECTURE.md) · [Referencia API](docs/API.md) · [Guía de Contribución](CONTRIBUTING.md)

---

## Fase 4: Producción

### Monitoreo y Observabilidad

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

**Métricas expuestas (Prometheus):**

| Métrica | Tipo | Descripción |
|---|---|---|
| `raindrops_store_total` | Counter | Total de operaciones STORE |
| `raindrops_reconstruct_total` | Counter | Total de operaciones RECONSTRUCT |
| `raindrops_verify_total` | Counter | Total de operaciones VERIFY |
| `raindrops_store_duration_seconds` | Histogram | Latencia de STORE |
| `raindrops_reconstruct_duration_seconds` | Histogram | Latencia de RECONSTRUCT |
| `raindrops_drops_expired_total` | Counter | Drops expirados por Reaper |
| `raindrops_replication_lag_seconds` | Gauge | Lag de replicación entre peers |
| `raindrops_storage_nodes_up` | Gauge | Nodos de almacenamiento activos |

### Rotación de Claves

```java
// La masterKey se genera por operación y nunca se almacena
// Los API Keys se rotan vía variable de entorno
// Los drops expiran automáticamente por TTL
// El Witness Node no almacena estado → cero superficie de ataque
```

### Rate Limiting

```yaml
# Configuración por defecto
rate-limiting:
  store: 100/min      # Operaciones STORE por minuto
  reconstruct: 50/min # Operaciones RECONSTRUCT por minuto
  verify: 200/min     # Operaciones VERIFY por minuto
```

### Health Checks

```bash
# Todos los nodos exponen:
GET /health           # Estado del servicio
GET /actuator/health  # Spring Boot Actuator (detallado)
GET /actuator/prometheus  # Métricas Prometheus
```

---

## Inicio Rápido

### Requisitos

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (para despliegue)

### Construir y probar

```bash
# Construir núcleo criptográfico + ejecutar tests
mvn -f raindrops-fase1/raindrops/pom.xml install

# Construir nodo de almacenamiento + ejecutar tests
mvn -f raindrops-fase1/storage/pom.xml test

# Construir nodo testigo + ejecutar tests
mvn -f raindrops-fase1/witness/pom.xml test
```

### Desplegar con Docker

```bash
# Desarrollo (sin autenticación)
docker compose -f docker-compose.yml up -d --build

# Producción (con API Key)
API_KEY=mi-clave-secreta docker compose -f docker-compose.yml up -d --build
```

### Desplegar en Raspberry Pi

```bash
# Clonar en la Pi
ssh serpico@<ip-pi>
git clone http://ERAC@<gitea-ip>:3000/ERAC/raindrops-fase1.git
cd raindrops-fase1

# Build multi-arch (ARM64)
docker compose -f docker-compose.yml build

# Ejecutar
docker compose -f docker-compose.yml up -d

# Verificar
curl http://localhost:9081/health
curl http://localhost:9080/health
```

---

## Uso de la API

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

// Conectar a 3 nodos de almacenamiento
List<String> nodes = List.of(
    "http://localhost:9081",
    "http://localhost:9082",
    "http://localhost:9083"
);

try (RainClient client = new RainClient(nodes)) {
    // Almacenar: 5 drops, K=3, TTL 30 días
    String rainMapId = client.store(
        "Mi secreto".getBytes(), 5, 3, 30
    );
    System.out.println("Almacenado: " + rainMapId);

    // Recuperar
    byte[] data = client.retrieve(rainMapId, masterKey);
    System.out.println("Recuperado: " + new String(data));
}
```

### API REST (cURL)

```bash
# ─── ALMACENAR vía Witness ────────────────────────────────
curl -X POST http://localhost:9080/witness/store \
  -H "Content-Type: application/json" \
  -d '{
    "data": "'$(echo -n "Mi dato secreto" | base64)'",
    "n": 5,
    "k": 3,
    "ttlDays": 30
  }'

# Respuesta:
# {
#   "rainMapId": "a1b2c3...",
#   "masterKeyHex": "012345..."
# }

# ─── RECONSTRUIR vía Witness ──────────────────────────────
curl -X POST http://localhost:9080/witness/reconstruct \
  -H "Content-Type: application/json" \
  -d '{
    "rainMapId": "a1b2c3...",
    "masterKeyHex": "012345..."
  }'

# Respuesta:
# {
#   "success": true,
#   "data": "TWkgZGF0byBzZWNyZXRv",  // base64
#   "n": 5,
#   "k": 3
# }

# ─── VERIFICAR un drop ────────────────────────────────────
curl -X POST http://localhost:9080/witness/verify \
  -H "Content-Type: application/json" \
  -d '{
    "dropJson": "{...}",
    "masterKeyHex": "012345..."
  }'

# ─── VERIFICAR salud ──────────────────────────────────────
curl http://localhost:9081/health
# {"status":"UP","service":"storage","node":"keeper-1"}

curl http://localhost:9080/health
# {"status":"UP","service":"witness"}
```

---

## Casos de Uso

### 1. Backup Resistente a Ransomware

```
Problema:  Ransomware cifra tus backups. Necesitas pagar o perder datos.
Solución:  Rain Drops fragmenta backups en 5 nodos. K=3 para restaurar.
           Un atacante que robe 2 nodos obtiene solo ruido.
Resultado: Tus backups son inútiles para el atacante.
```

### 2. Almacenamiento Confidencial Distribuido

```
Problema:  Datos sensibles (médicos, financieros) en un servidor central.
Solución:  Distribuir fragments entre jurisdictions/entidades.
           Se necesita cooperación de K entidades para acceder.
Resultado: Ninguna entidad individual puede acceder a los datos.
```

### 3. Gestión de Secretos Empresariales

```
Problema:  Claves API, certificados, secretos en un gestor central.
Solución:  Cada secreto se fragmenta entre N nodos.
           K=3 para reconstruir (ej: CTO + Director + Auditor).
Resultado: Ningún individuo puede acceder solo a los secretos.
```

### 4. Cumplimiento Normativo Automático

```
Problema:  GDPR/HIPAA requieren que los datos sean inaccesibles.
Solución:  La confidencialidad es estructural, no procedimental.
           Los datos son irrecuperables sin K partes + masterKey.
Resultado: Cumplimiento automático por diseño, no por política.
```

### 5. Almacenamiento Zero-Knowledge

```
Problema:  El proveedor de cloud puede ver tus datos.
Solución:  El storage node NUNCA ve datos en claro.
           Solo almacena drops (ruido aleatorio).
Resultado: Ni el administrador del sistema puede acceder.
```

---

## Seguridad en Producción

### Checklist de Despliegue

- [ ] API Key habilitada en todos los nodos de almacenamiento
- [ ] TLS habilitado (keystore montado vía volumen Docker)
- [ ] Rate limiting configurado según load esperado
- [ ] Prometheus + Grafana configurados para monitoreo
- [ ] Alertas configuradas (nodos caídos, replicación lenta)
- [ ] TTL apropiado para el caso de uso (no mayor al necesario)
- [ ] Backup de RainMaps fuera de la red principal
- [ ] Rotación de API Keys programada

### Recomendaciones

1. **Nunca usar K=2 en producción** — Mínimo K=3 para tolerancia a un drop malicioso
2. **TTL corto es mejor** — Menos ventana de ataque
3. **Monitorear replicación** — Si un nodo está lento, los drops pueden perderse
4. **Backup del RainMap** — Sin el RainMap, los drops son inútiles
5. **Auditoría de logs** — Quién hizo STORE/RECONSTRUCT y cuándo

---

## Estructura del Proyecto

```
raindrops-fase1/
├── raindrops/                    ← Núcleo criptográfico
│   └── src/main/java/io/raindrops/
│       ├── core/                 ← ShamirSSS, HybridScheme, RainDropsCore
│       └── client/               ← RainClient SDK
├── storage/                      ← Nodo de Almacenamiento
│   └── src/main/java/io/raindrops/storage/
│       ├── controller/           ← REST + Dashboard UI
│       ├── service/              ← DropService, RainMapService, ReplicationService
│       ├── model/                ← Entidades JPA
│       ├── repository/           ← Repositorios Spring Data
│       └── config/               ← PeerConfig, ReaperConfig, SecurityConfig
├── witness/                      ← Nodo Testigo
│   └── src/main/java/io/raindrops/witness/
│       ├── controller/           ← REST API
│       └── service/              ← WitnessService
├── docs/                         ← Documentación y demo web
├── Dockerfile.storage            ← Build multi-stage ARM64/AMD64
├── Dockerfile.witness            ← Build Docker del Witness
├── docker-compose.yml            ← Orquestación 3 nodos + witness
└── prometheus.yml                ← Configuración Prometheus
```

---

## Roadmap

| Fase | Estado | Descripción |
|---|---|---|
| Fase 1 | [DONE] Completada | Núcleo criptográfico: ShamirSSS, HybridScheme, RainDropsCore |
| Fase 2 | [DONE] Completada | Storage Node: Spring Boot, JPA, H2, replicación, Dashboard |
| Fase 3 | [DONE] Completada | Witness Node: Verificación, Auth/TLS, RainMap replication, CI/CD |
| **Fase 4** | [...] **En progreso** | **Producción: Monitoreo, Rate Limiting, Health Checks, Hardening** |
| Fase 5 | TODO Planificada | Distribución geográfica, consensus entre nodos, key rotation |
| Fase 6 | TODO Planificada | Clientes multi-lenguaje (Python, JS, Go), API pública |

---

## Publicaciones Académicas

> Ramírez Castillo, E. A. (2026). *Rain Drops: Un Modelo Teórico de Almacenamiento Distribuido de Información Basado en Criptografía de Umbral y Fragmentación Semántica.*

> Ramírez Castillo, E. A. (2026). *Rain Drops — Fase 1: Implementación del Núcleo Criptográfico.*

---

## Contribuir

Las contribuciones son bienvenidas. Por favor, lee `CONTRIBUTING.md` antes de enviar un Pull Request.

---

## Licencia

Este proyecto se publica bajo la **Licencia MIT**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
