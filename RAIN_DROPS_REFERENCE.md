# RAIN DROPS — Documento Interno de Referencia
> Última actualización: 2026-07-15 | Estado: Beta (Fase 4)
> Autor: Edwar Antonio Ramírez Castillo (erac73)

---

## 1. ACCESO RÁPIDO

### Infraestructura
| Recurso | Valor |
|---------|-------|
| Pi SSH | `ssh serpico@100.109.105.19` (pass: `Nu6EuwrR%ij7`) |
| Repo GitHub | `https://github.com/erac73/raindrops` |
| Repo Gitea local | `http://192.168.100.10:3000/ERAC/raindrops-fase1` |
| GitHub PAT | `ghp_<TU_PAT_AQUI>` (nunca commitear PATs reales) |
| Project Pages (Pi) | `http://100.109.105.19:8089` |
| Project Pages (GH) | `https://erac73.github.io/raindrops/` |

### Servicios Docker — Rain Drops
| Servicio | Container | Puerto | URL interna |
|----------|-----------|--------|-------------|
| witness-node | raindrops-witness | 9080:8080 | http://witness:8080 |
| storage-node-1 | raindrops-storage-1 | 9081:8080 | http://keeper-1:8080 |
| storage-node-2 | raindrops-storage-2 | 9082:8080 | http://keeper-2:8080 |
| storage-node-3 | raindrops-storage-3 | 9083:8080 | http://keeper-3:8080 |
| docs (nginx) | raindrops-docs | 8089:80 | — |

### Servicios Docker — Infra general
| Servicio | Puerto | URL externa |
|----------|--------|-------------|
| Emby | 8096 | mipi.dpdns.org |
| PhotoPrism | 2342 | photos.mipi.dpdns.org |
| Open WebUI | 3001 | ai.mipi.dpdns.org |
| Uptime Kuma | 3003 | status.mipi.dpdns.org |
| Gitea | 3000 | git.mipi.dpdns.org |
| Portainer | 9000/9443 | — |
| PostgreSQL | 5432 | — |
| OwnlyLearn Auth | 8083 | — |
| OwnlyLearn Course | 8082 | — |
| Ollama | 11434 | — |

### Rutas críticas en el Pi
```
/home/serpico/raindrops-fase1/                              # Repo raíz
/home/serpico/raindrops-fase1/raindrops-fase1/raindrops/    # Core library (sin Spring)
/home/serpico/raindrops-fase1/raindrops-fase1/witness/      # Witness module
/home/serpico/raindrops-fase1/raindrops-fase1/storage/      # Storage module
/home/serpico/raindrops-fase1/docs/                         # Docs HTML → GitHub Pages
/home/serpico/raindrops-fase1/docker-compose.yml            # Docker compose
/tmp/cp.txt                                                 # Maven classpath cache
/mnt/m2-storage/                                            # NVMe (Docker data-root) — vía USB
/mnt/storage/                                               # SSD 128GB (backups, media)
```

---

## 2. ARQUITECTURA

### Diagrama de componentes
```
┌──────────────────────────────────────────────────────────┐
│                    Client (RainClient SDK)                │
│                         │                                 │
│                    DROP / RECONSTRUCT                     │
└─────────────────────┬────────────────────────────────────┘
                      │
         ┌────────────▼────────────┐
         │    WITNESS NODE (9080)   │
         │  Stateless coordinator  │
         │  - Fragmenta datos      │
         │  - Distribuye drops     │
         │  - Verifica HMAC+TTL    │
         │  - Reconstruye datos    │
         └──┬──────┬──────┬───────┘
            │      │      │
     ┌──────▼──┐ ┌─▼────┐ ┌▼──────┐
     │KEEPER-1 │ │KEEPER│ │KEEPER │
     │  (9081) │ │-2    │ │-3     │
     │  H2 DB  │ │(9082)│ │(9083) │
     │  peer   │ │ H2 DB│ │ H2 DB │
     │  repl.  │ │ peer │ │ peer  │
     └─────────┘ └──────┘ └───────┘
       ←─── replicación peer-to-peer async ───→
```

### Flujo DROP (6 pasos)
1. Datos > 65 bytes → AES-256-GCM genera clave + ciphertext
2. Clave maestra (32 bytes) → `ShamirSSS.split(N, K)` sobre GF(2⁵²¹−1)
3. Cada share → `Drop(id, x, y, HMAC-SHA256, TTL)`
4. Drops distribuidos a N Storage Nodes via REST
5. RainMap encriptado creado (índice drop→nodo)
6. RainMap + ciphertext almacenados en primer nodo

### Flujo RECONSTRUCT (6 pasos)
1. Fetch RainMap del storage → desencripta con masterKey
2. Recolecta K drops de sus nodos respectivos
3. Verifica HMAC + TTL de cada drop
4. Interpolación de Lagrange recupera clave AES
5. AES-256-GCM descifra → datos originales
6. Retorna resultado con info de drops inválidos

### Propiedades criptográficas
- **Perfect secrecy**: con < K drops, la distribución del secreto es uniforme
- **IND-CCA2 secure** (AES-256-GCM + Shamir SSS)
- **GF(2⁵²¹−1)**: primo de Mersenne, 521 bits
- **HMAC-SHA256**: integridad por drop (comparación constante anti-timing)
- **TTL**: drops expirados se eliminan automáticamente (reaper cada 60s)

---

## 3. MAPA DE ARCHIVOS CRÍTICOS

### raindrops-core (librería pura, sin Spring)

| Archivo | Líneas | Qué hace | Líneas clave |
|---------|--------|----------|--------------|
| `ShamirSSS.java` | 244 | Split/combine sobre GF(p) | L9: p=2^521-1, L55: split(), L110: combine() |
| `RainDropsCore.java` | 239 | Fachada principal DROP/RECONSTRUCT | L50: drop(), L120: reconstruct(), L170: hybrid path |
| `RainMap.java` | 180 | Índice encriptado AES-GCM | L40: create(), L85: seal(), L100: fromEncrypted() **[BUG L98]** |
| `HybridScheme.java` | 213 | AES-256-GCM encrypt/decrypt | L40: encrypt(), L90: decrypt(), BC GCMBlockCipher |
| `Drop.java` | 97 | Modelo inmutable: id,x,y,mac,ttl | L15: campos, L60: isExpired() |
| `DropFactory.java` | 185 | Crear/verificar drops | L30: create(), L80: verify() [const-time] |
| `DropSerializer.java` | 98 | JSON ser/deser con Jackson | Hex encoding para byte[] y BigInteger |
| `RainClient.java` | — | SDK HTTP (Java 11+ HttpClient) | store() y retrieve() |

### raindrops-storage

| Archivo | Líneas | Qué hace | Líneas clave |
|---------|--------|----------|--------------|
| `DropController.java` | 121 | REST: /drops, /rainmaps, /health | L20: POST /drops, L50: GET /drops/{id} |
| `DropService.java` | 89 | Store/get drops + peer fallback | L25: storeDrop(), L50: getDrop() |
| `RainMapService.java` | 94 | Store/get rainmaps + replicate | L20: storeRainMap(), L60: getRainMap() |
| `ReplicationService.java` | 108 | Async peer replication | L30: replicateDrop(), L60: tryFetchFromPeers() |
| `PeerConfig.java` | — | Parsea PEER_URLS env var | getPeerUrls(), getMyUrl() |
| `SecurityConfig.java` | — | Spring Security + API key | ApiKeyFilter when API_KEY set |
| `RateLimitConfig.java` | — | Token bucket por IP | POST:100/min, GET:200/min |
| `ReaperConfig.java` | — | @Scheduled TTL cleanup cada 60s | deleteByTtlBefore() |
| `StorageHealthIndicator.java` | — | Actuator: mem, disk, uptime | WARNING si mem>85% o disk>90% |

### raindrops-witness

| Archivo | Líneas | Qué hace | Líneas clave |
|---------|--------|----------|--------------|
| `WitnessController.java` | 93 | REST: /witness/store, /reconstruct, /verify | L25: POST store, L50: POST reconstruct |
| `WitnessService.java` | 232 | Orquestador stateless | L50: storeData(), L100: reconstruct() **[DEBUG LOGS]** |
| `WitnessHealthIndicator.java` | — | Health: role=witness, nodes, mem | — |
| `WitnessRateLimitConfig.java` | — | POST:50/min, GET:100/min | — |

---

## 4. BUGS CONOCIDOS

### BUG-001: RainMap.create() hardcodea k=n
- **Archivo**: `raindrops-fase1/raindrops/src/main/java/io/raindrops/core/RainMap.java`
- **Línea**: ~98
- **Problema**: `int k = n;` — siempre usa k=n en vez del k solicitado
- **Impacto**: Todos los RainMaps se crean con threshold=n (máximo), no con el k real
- **Ejemplo**: Si drop(3,2) → RainMap dice k=3 en vez de k=2
- **Fix**: `create()` debe aceptar k como parámetro explícito

### BUG-002: Debug logging sin limpiar
- **Archivo**: `witness/src/main/java/io/raindrops/witness/service/WitnessService.java`
- **Líneas**: 89-90, 98, 182, 197
- **Problema**: Logs [DEBUG-STORE] y [DEBUG-RECON] con datos sensibles (masterKey hex)
- **Impacto**: Información criptográfica en logs de producción
- **Fix**: Eliminar o cambiar a `log.debug()` con conditional

### BUG-003: NVMe PCIe desconectada
- **Dispositivo**: WD PC SN740 256GB (originalmente /dev/sdb, ahora /dev/sdc vía USB)
- **Problema**: La NVMe se desconectó del bus PCIe nativo del Pi5
- **Impacto**: Docker data-root ahora vía USB adapter (JMS583) → menor rendimiento
- **Estado**: Funcional pero lento. SMART PASSED, sin errores en el disco
- **Fix físico**: Revisar HAT PCIe del Pi5, apretar conector M.2

---

## 5. COMANDOS UTILITARIOS

### Build
```bash
# Build completo (desde raindrops-fase1/)
cd /home/serpico/raindrops-fase1
docker compose build witness-node
docker compose build storage-node-1

# Build solo core (para tests)
cd raindrops-fase1/raindrops
mvn clean install -DskipTests

# Build witness
cd ../witness
mvn package -DskipTests -q

# Build storage
cd ../storage
mvn package -DskipTests -q
```

### Deploy
```bash
cd /home/serpico/raindrops-fase1
docker compose up -d witness-node storage-node-1 storage-node-2 storage-node-3 docs

# Verificar
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep rain
```

### Test
```bash
# Unit tests (core)
cd raindrops-fase1/raindrops && mvn test

# Compilation classpath para tests manuales
mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt

# Test de integración witness (store → reconstruct)
curl -X POST http://localhost:9080/witness/store \
  -H 'Content-Type: application/json' \
  -d '{"data":"SGVsbG8gUmFpbiBEcm9wcyE=","n":3,"k":2,"ttlDays":30}'

# Test python (desde /tmp)
python3 /tmp/witness_test.py
```

### Logs y troubleshooting
```bash
# Logs witness
docker logs raindrops-witness --tail 50

# Logs storage
docker logs raindrops-storage-1 --tail 50

# Verificar estado de Docker
sudo systemctl status docker
sudo docker ps -a --format 'table {{.Names}}\t{{.Status}}'

# Si Docker no arranca (NVMe):
sudo umount -l /mnt/m2-storage
sudo mount /dev/sdc1 /mnt/m2-storage
sudo systemctl reset-failed docker
sudo systemctl start docker

# Si contenedores están muertos (exit 135):
cd /home/serpico/raindrops-fase1 && docker compose up -d

# Si hay conflictos de nombres:
docker rm -f <container-name>
docker compose up -d
```

### Git (push a ambos remotos)
```bash
cd /home/serpico/raindrops-fase1
git add -A && git commit -m 'mensaje'
git push origin main    # Gitea local
git push github main    # GitHub (requiere PAT configurado)
```

---

## 6. ESTADO ACTUAL (Snapshot 2026-07-15)

### Funcionando
- [x] Store→Reconstruct end-to-end (verificado hoy)
- [x] 3 storage nodes + 1 witness + docs Docker
- [x] 20 contenedores Docker corriendo en Pi5
- [x] Documentación EN/ES completa
- [x] Demo interactiva (HTML)
- [x] GitHub Pages con index + demo
- [x] Uptime Kuma: 12 monitores activos
- [x] Cloudflare Tunnel para servicios web

### Pendiente
- [ ] Fix RainMap.create() bug (k=n hardcoded)
- [ ] Limpiar debug logs de WitnessService
- [ ] Fix conexión PCIe NVMe (físico)
- [ ] Tests para módulos storage y witness
- [ ] CI/CD pipeline completo (build.yml, deploy.yml existen)
- [ ] Rate limiting configurable por env var
- [ ] Dashboard web mejorado para storage nodes

---

## 7. NOTAS DE DEBUG

### Historial del bug AES-GCM (resuelto 2026-07-15)

**Síntoma**: Witness store→reconstruct fallaba con `AESGCM InvalidTag`

**Pruebas realizadas**:
1. Python AESGCM directo (store→fetch→decrypt) → **FUNCIONA** ✓
2. Java in-memory seal→unseal → **FUNCIONA** ✓
3. Java RestClient test (store→fetch) → **FUNCIONA** ✓
4. BouncyCastle↔Python compatibilidad → **FUNCIONA** ✓
5. Witness HTTP store→reconstruct → **FALLABA** ✗

**Root cause**: La NVMe M.2 (`/dev/sdb`) estaba experimentando errores de E/S
catastróficos. Los directorios ext4 estaban corruptos (`error -5 reading directory
block` en 726 entradas del dmesg). Cuando el witness intentaba leer los datos
almacenados, recibía basura criptográfica que causaba InvalidTag.

**Resolución**: La NVMe se desconectó del PCIe y reapareció vía USB adapter.
Al remontar el filesystem limpio, los datos se leyeron correctamente y el
store→reconstruct funcionó.

**Lección**: Siempre verificar la integridad del storage subyacente antes de
diagnosticar bugs criptográficos.

### Patrón de debug para el futuro
1. Verificar `docker ps` — todos los contenedores Up?
2. Verificar `docker logs` — errores de E/S o crypto?
3. Test Python directo vs Java HTTP — comparar hex output
4. Verificar SMART del disco: `sudo smartctl -H /dev/sdX`
5. Verificar dmesg: `sudo dmesg -T | grep -i error`

### Dockerfile patterns
- Multi-stage build: `maven:3.9-eclipse-temurin-17` → `eclipse-temurin:17-jre`
- Usuario no-root: `raindrops:raindrops`
- Entrypoint script: `entrypoint.sh`
- Dependencias core se copian primero para cache

### Spring Boot patterns
- Config vía env vars: NODE_ID, PEER_URLS, API_KEY, STORAGE_URLS
- H2 embebido con JPA (file-based para persistencia entre restarts)
- Actuator en /actuator/health con indicadores custom
- Micrometer + Prometheus para métricas
- Rate limiting: token bucket por IP + método HTTP
- Audit logging: UUID requestId + client IP + method + URI + status + duration
