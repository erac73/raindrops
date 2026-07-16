# RAIN DROPS — Lista de Prioridades (Revisión de Código)
> Generado: 2026-07-15 | Basado en revisión completa de código fuente
> Priorizado por severidad: CRÍTICO → ALTO → MEDIO → BAJO

---

## Bugs Encontrados en el Código

### BUG-001: RainMap.create() hardcodea k=n [CRÍTICO]
- **Archivo**: `raindrops-core/RainMap.java:62`
- **Código**:
  ```java
  int n = nodeUrls.size();
  int k = n;  // ← BUG: debería ser el k real del split
  ```
- **Impacto**: TODOS los RainMaps se crean con k=n, forzando que se necesiten TODOS los drops para reconstruir. Anula el umbral K.
- **Fix**: Cambiar `create(drops, nodeUrls, masterKey)` a `create(drops, nodeUrls, masterKey, k)` y usar el k real.
- **Afecta**: storeData() en WitnessService, store() en RainClient, buildAndStoreRainMap() en DropController.
- **Esfuerzo**: 1 hora

### BUG-002: ApiKeyFilter usa String.equals() (timing-unsafe) [ALTO]
- **Archivo**: `storage/config/ApiKeyFilter.java`
- **Código**: `providedKey.equals(expectedApiKey)`
- **Impacto**: Comparación no constante en tiempo. Un atacante podría inferir la API key longitud por longitud via timing attack.
- **Fix**: Usar `MessageDigest.isEqual()` o comparación byte-a-byte con XOR.
- **Esfuerzo**: 30 minutos

### BUG-003: ReplicationService no tiene timeouts [ALTO]
- **Archivo**: `storage/service/ReplicationService.java`
- **Impacto**: Si un peer no responde, el hilo CompletableFuture queda bloqueado indefinidamente. Puede agotar el CachedThreadPool.
- **Fix**: Añadir timeout a RestClient (ej: 5s) y usar CompletableFuture.orTimeout().
- **Esfuerzo**: 1 hora

### BUG-004: RateLimitConfig tiene memory leak en buckets [MEDIO]
- **Archivo**: `storage/config/RateLimitConfig.java` y `witness/config/WitnessRateLimitConfig.java`
- **Código**: `buckets.computeIfAbsent(key, k -> new RateBucket(...))`
- **Impacto**: Cada IP+method crea un bucket que NUNCA se elimina. Un atacante puede enviar desde IPs spoofed y llenar memoria.
- **Fix**: Usar Caffeine cache con TTL o ConcurrentHashMap con cleanup periódico.
- **Esfuerzo**: 2 horas

### BUG-005: getRainMap() retorna JSON manual sin escape [MEDIO]
- **Archivo**: `storage/service/RainMapService.java`
- **Código**: Concatenación manual de strings para JSON.
- **Impacto**: Si algún valor contiene comillas o caracteres especiales, el JSON se rompe.
- **Fix**: Usar ObjectMapper o StringBuilder con escape.
- **Esfuerzo**: 1 hora

### BUG-006: WitnessService tiene debug logging sensible [MEDIO]
- **Archivo**: `witness/service/WitnessService.java`
- **Código**: `log.info("[DEBUG-STORE] payloadHex len: {}", ...)` y `[DEBUG-RECON]` con masterKeyHex.
- **Impacto**: Expone masterKey y payload en logs de producción.
- **Fix**: Eliminar o cambiar a log.debug() con condición.
- **Esfuerzo**: 30 minutos

### BUG-007: DropRepository.deleteByTtlBefore() falta @Transactional [BAJO]
- **Archivo**: `storage/repository/DropRepository.java`
- **Impacto**: Spring Data JPA lo maneja implícitamente, pero es mejor explícito para auditoría.
- **Fix**: Añadir @Transactional en DropService.reapExpiredDrops().
- **Esfuerzo**: 15 minutos

### BUG-008: StorageHealthIndicator no cubre directorio H2 [BAJO]
- **Archivo**: `storage/config/StorageHealthIndicator.java`
- **Impacto**: El check de disco usa FileStore del filesystem actual, no el directorio /app/data donde está la BD H2.
- **Fix**: Cambiar Paths.get(".") a Paths.get("/app/data").
- **Esfuerzo**: 15 minutos

---

## Falta de Features (vs fragmentiX)

### FEAT-001: Verifiable Secret Sharing (VSS) [CRÍTICO]
- **Estado**: ❌ No implementado
- **Impacto**: Sin VSS, un nodo malicioso puede enviar shares falsos durante reconstruct sin ser detectado.
- **Implementación**: Feldman's VSS — agregar generación de commitments y verificación de shares.
- **Archivos afectados**: ShamirSSS.java (nuevo VerifiableSSS), WitnessService.java
- **Esfuerzo**: 2 semanas

### FEAT-002: Proactive Share Refresh [CRÍTICO]
- **Estado**: ❌ No implementado
- **Impacto**: Un adversario que compromete K shares a lo largo del tiempo puede reconstruir el secreto.
- **Implementación**: Protocolo distribuido de refresh entre storage nodes.
- **Archivos afectados**: Nuevo RefreshService.java, ReplicationService.java
- **Esfuerzo**: 2 semanas

### FEAT-003: S3 Compatibility Layer [ALTO]
- **Estado**: ❌ No implementado
- **Impacto**: Sin API S3, Rain Drops no puede usarse como drop-in replacement de fragmentiX.
- **Implementación**: Endpoint PUT/GET/DELETE/HEAD /{bucket}/{key} compatible con AWS CLI.
- **Archivos afectados**: Nuevo módulo s3-proxy o extensión de DropController.
- **Esfuerzo**: 3-4 semanas

### FEAT-004: JavaScript/TypeScript SDK [ALTO]
- **Estado**: ❌ No implementado
- **Impacto**: Sin client web, no hay ecosistema de aplicaciones.
- **Implementación**: Paquete npm @raindrops/client con Web Crypto API.
- **Esfuerzo**: 2 semanas

### FEAT-005: Self-Healing de shares [ALTO]
- **Estado**: ❌ No implementado
- **Impacto**: Si un nodo muere permanentemente, los drops se pierden sin recuperación.
- **Implementación**: Heartbeat entre nodos + reconstrucción distribuida de shares.
- **Esfuerzo**: 3 semanas

### FEAT-006: Configuración dinámica (Hot Reload) [ALTO]
- **Estado**: ❌ No implementado (solo env vars)
- **Impacto**: Reconfigurar el cluster requiere reiniciar Docker.
- **Implementación**: Endpoint PUT /admin/config + @RefreshScope.
- **Esfuerzo**: 1 semana

### FEAT-007: RBAC (Role-Based Access Control) [MEDIO]
- **Estado**: ❌ No implementado (solo API key binaria)
- **Impacto**: No hay roles (admin/writer/reader). Un solo nivel de acceso.
- **Implementación**: JWT con roles embebidos + tabla users en H2.
- **Esfuerzo**: 2 semanas

### FEAT-008: Post-Quantum Hybrid Mode [MEDIO]
- **Estado**: ❌ No implementado
- **Impacto**: SSS no tiene protección post-quantum formal.
- **Implementación**: CRYSTALS-Kyber como capa adicional de key encapsulation.
- **Esfuerzo**: 2 semanas

### FEAT-009: Compliance Reports (SOC2, NIS2) [MEDIO]
- **Estado**: ❌ No implementado
- **Impacto**: Sin reports formales, no se puede vender a gobiernos/empresas.
- **Implementación**: Endpoints de exportación de audit trail + health report.
- **Esfuerzo**: 2 semanas

### FEAT-010: Benchmark Suite [BAJO]
- **Estado**: ❌ No implementado
- **Impacto**: Sin métricas de rendimiento, no se puede demostrar superioridad.
- **Implementación**: Script de benchmark con comparativa contra fragmentiX/shard.
- **Esfuerzo**: 1 semana

---

## Calidad de Código

### CODE-001: Clases de entidades sin Records [MEDIO]
- **Archivos**: DropEntity.java, RainMapEntity.java
- **Problema**: Clases verbosas con getters/setters manuales.
- **Sugerencia**: Usar Java Records (inmutables) o Lombok @Data.
- **Esfuerzo**: 2 horas

### CODE-002: ReplicationService crea ObjectMapper por llamada [BAJO]
- **Archivo**: `storage/service/ReplicationService.java`
- **Problema**: `new ObjectMapper()` en cada llamada a replicateRainMap().
- **Fix**: Inyectar un ObjectMapper singleton.
- **Esfuerzo**: 15 minutos

### CODE-003: HealthIndicators duplicados [BAJO]
- **Archivos**: StorageHealthIndicator.java, WitnessHealthIndicator.java
- **Problema**: Código casi idéntico en ambos módulos.
- **Sugerencia**: Crear clase base AbstractHealthIndicator.
- **Esfuerzo**: 1 hora

### CODE-004: RateLimitConfig duplicado [BAJO]
- **Archivos**: storage/RateLimitConfig.java, witness/WitnessRateLimitConfig.java
- **Problema**: Código casi idéntico con mismos bugs.
- **Sugerencia**: Extraer a módulo compartido raindrops-core.
- **Esfuerzo**: 1 hora

### CODE-005: AuditLogInterceptor duplicado [BAJO]
- **Archivos**: storage/AuditLogInterceptor.java, witness/WitnessAuditLogInterceptor.java
- **Problema**: Código idéntico.
- **Sugerencia**: Mover a raindrops-core.
- **Esfuerzo**: 30 minutos

### CODE-006: Falta CORS en StorageApplication [BAJO]
- **Archivo**: `storage/StorageApplication.java`
- **Problema**: No hay configuración CORS. Si se accede desde un browser, las peticiones fallarán.
- **Esfuerzo**: 30 minutos

---

## Resumen de Priorización

| # | Item | Tipo | Severidad | Esfuerzo |
|---|------|------|-----------|----------|
| BUG-001 | RainMap k=n | Bug | CRÍTICO | 1h |
| BUG-002 | ApiKey timing | Bug | ALTO | 30min |
| BUG-003 | Replication timeout | Bug | ALTO | 1h |
| BUG-004 | RateLimit memory leak | Bug | MEDIO | 2h |
| BUG-005 | JSON manual sin escape | Bug | MEDIO | 1h |
| BUG-006 | Debug logging sensible | Bug | MEDIO | 30min |
| FEAT-001 | VSS | Feature | CRÍTICO | 2 sem |
| FEAT-002 | Proactive Refresh | Feature | CRÍTICO | 2 sem |
| FEAT-003 | S3 Compatibility | Feature | ALTO | 3-4 sem |
| FEAT-004 | JS/TS SDK | Feature | ALTO | 2 sem |
| FEAT-005 | Self-Healing | Feature | ALTO | 3 sem |
| FEAT-006 | Hot Reload | Feature | ALTO | 1 sem |
| CODE-001 | Entity Records | Calidad | MEDIO | 2h |
| CODE-002 | ObjectMapper reuse | Calidad | BAJO | 15min |
| CODE-003-006 | Duplicación de código | Calidad | BAJO | 3h total |

---

## Orden de Implementación Recomendado

### Fase 0 — Bugs críticos (1 día)
1. BUG-001: Fix RainMap k=n
2. BUG-002: Fix ApiKey timing
3. BUG-006: Limpiar debug logging

### Fase 1 — Seguridad base (4 semanas)
4. FEAT-001: VSS (Feldman's)
5. FEAT-002: Proactive Refresh

### Fase 2 — Diferenciación (6 semanas)
6. FEAT-003: S3 Compatibility Layer
7. FEAT-004: JS/TS SDK
8. FEAT-006: Hot Reload

### Fase 3 — Resiliencia (3 semanas)
9. FEAT-005: Self-Healing
10. BUG-003: Replication timeouts
11. BUG-004: RateLimit fix

### Fase 4 — Enterprise (5 semanas)
12. FEAT-007: RBAC
13. FEAT-008: PQC Hybrid
14. FEAT-009: Compliance Reports

### Fase 5 — Calidad (1 semana)
15. CODE-001-006: Refactoring y limpieza
16. FEAT-010: Benchmark Suite
