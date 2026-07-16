# RAIN DROPS — Lista de Mejoras Pendientes
> Generado: 2026-07-15 | Comparado contra: fragmentiX, Vault12, shard
> Priorizado por impacto de negocio y diferenciación

---

## Resumen Ejecutivo

Rain Drops tiene una arquitectura sólida pero le faltan features de seguridad y funcionalidad que fragmentiX ya ofrece. Las mejoras están priorizadas en 3 niveles: CRÍTICO (paridad de seguridad), ALTA (diferenciación), MEDIA (futuro-proofing).

---

## NIVEL 1 — CRÍTICO (Sin esto no es competitivo)

### IMP-001: Verifiable Secret Sharing (VSS) — Feldman's Scheme
- **Estado**: ❌ No implementado
- **Prioridad**: CRÍTICO
- **Esfuerzo**: ~200 líneas Java, 1-2 semanas
- **Archivo afectado**: `raindrops-core/ShamirSSS.java`, nuevo `VerifiableSSS.java`

**Qué es**: Sin VSS, un nodo malicioso puede entregar un share falso durante reconstruct y destruir el secreto silenciosamente. No hay forma de verificar la honestidad de un share.

**Cómo funciona Feldman's VSS**:
1. Dealer genera polinomio secreto: f(x) = a₀ + a₁x + a₂x² + ... + aₖ₋₁xᵏ⁻¹
2. Para cada share i, calcula commitment: Cⱼ = g^aⱼ mod p para j=0..k-1
3. Publica commitments {C₀, C₁, ..., Cₖ₋₁}
4. Cada shareholder verifica: g^yᵢ ≡ ∏ⱼ Cⱼ^xᵢʲ mod p
5. Si falla, el share es inválido y se rechaza

**Implementación**:
- Agregar clase `VerifiableSSS` extiende `ShamirSSS`
- Método `splitWithCommitment()` retorna (shares, commitments)
- Método `verifyShare()` verifica un share contra commitments
- Método `combineVerified()` solo acepta shares verificados
- commitments se almacenan en RainMap (son compactos: k puntos en Zp)

**Impacto**: Elimina el vector de ataque de shares falsos. Paridad con fragmentiX.

---

### IMP-002: Proactive Share Refresh
- **Estado**: ❌ No implementado
- **Prioridad**: CRÍTICO
- **Esfuerzo**: ~300 líneas Java, 2 semanas
- **Archivo afectado**: `storage/ReplicationService.java`, nuevo `RefreshService.java`

**Qué es**: Sin refresh, un adversario que compromete K shares diferentes a lo largo del tiempo (no simultáneamente) puede reconstruir el secreto. El refresh renueva los shares sin cambiar el secreto.

**Cómo funciona**:
1. Cada storage node i genera polinomio aleatorio: rᵢ(x) donde rᵢ(0)=0
2. Cada nodo calcula su share refresh: Δᵢⱼ = rᵢ(j) para cada nodo j
3. Los nodos intercambian Δᵢⱼ de forma segura (P2P)
4. Cada nodo actualiza: share_j = share_j + Σᵢ Δᵢⱼ mod p
5. Los shares viejos se invalidan, los nuevos sirven para el mismo secreto

**Implementación**:
- Nuevo `RefreshService` con @Scheduled (cada 24h o configurable)
- Protocolo P2P entre storage nodes (ya existe `ReplicationService`)
- Los nodos generan polinomios aleatorios via `ShamirSSS.generatePolynomial()`
- Intercambio de deltas via POST /refresh/delta
- Al finalizar, cada nodo actualiza sus shares en H2
- Verificar que K shares nuevos aún reconstruyen el secreto

**Impacto**: Seguridad temporal. Los shares comprometidos anteriormente se vuelven inútiles.

---

## NIVEL 2 — ALTA (Diferenciación de mercado)

### IMP-003: S3 Compatibility Layer (KILLER FEATURE)
- **Estado**: ❌ No implementado
- **Prioridad**: ALTA
- **Esfuerzo**: ~500 líneas Java, 3-4 semanas
- **Archivo afectado**: Nuevo módulo `s3-proxy/` o extensión de `storage/`
- **Dependencia**: aws-sdk-java-s3 (solo interfaz, no AWS real)

**Qué es**: fragmentiX se vende como "drop-in S3 proxy". Si Rain Drops expone una API compatible con S3, cualquier aplicación existente funciona sin cambios: rclone, duplicity, backup tools, cloud sync, etc.

**Cómo implementarlo**:
1. Endpoint `PUT /{bucket}/{key}` con Content-Type y body
   - Acepta datos como S3 PutObject
   - Internamente: drop(data, N=5, K=3, ttlDays=30)
   - Retorna 200 con ETag (rainMapId)
2. Endpoint `GET /{bucket}/{key}`
   - Internamente: reconstruct(rainMapId, masterKey)
   - Retorna 200 con el dato original + Content-Type
3. Endpoint `HEAD /{bucket}/{key}` — verificar existencia
4. Endpoint `DELETE /{bucket}/{key}` — eliminar drops
5. Endpoint `GET /{bucket}?list-type=2` — listar keys (rainMapIds)
6. Compatible con AWS CLI: `aws --endpoint-url http://pi:9080 s3 cp file.txt s3://mybucket/`

**Configuración por bucket**:
```json
{
  "bucket": "my-secure-backup",
  "n": 5,
  "k": 3,
  "ttlDays": 365,
  "storageNodes": ["keeper-1", "keeper-2", "keeper-3", "keeper-4", "keeper-5"]
}
```

**Impacto**: Abre todo el ecosistema S3. Cualquier herramienta que use S3 funciona con Rain Drops automáticamente.

---

### IMP-004: JavaScript/TypeScript SDK
- **Estado**: ❌ No implementado
- **Prioridad**: ALTA
- **Esfuerzo**: ~400 líneas TypeScript, 2 semanas
- **Nuevo paquete**: `@raindrops/client` (npm)

**Qué es**: Sin client web/mobile, no hay ecosistema. Vault12 tiene app móvil, fragmentiX tiene S3 proxy, Rain Drops solo tiene Java SDK.

**Implementación**:
```typescript
import { RainDropsClient } from '@raindrops/client';

const client = new RainDropsClient({ witnessUrl: 'http://pi:9080' });

// Store
const { rainMapId, masterKeyHex } = await client.store('Mi dato secreto', {
  n: 3, k: 2, ttlDays: 30
});

// Retrieve
const data = await client.retrieve(rainMapId, masterKeyHex);
```

**Features**:
- Compatible con browsers (Web Crypto API para AES-GCM nativo)
- Compatible con Node.js
- Métodos: `store()`, `retrieve()`, `verify()`, `status()`
- Sin dependencias pesadas (< 50KB bundle)
- TypeScript types incluidos

**Impacto**: Ecosistema web/mobile. Posibilita crear dashboards, apps móviles, browser extensions.

---

### IMP-005: Self-Healing (Auto-recuperación de shares)
- **Estado**: ❌ No implementado
- **Prioridad**: ALTA
- **Esfuerzo**: ~400 líneas Java, 3 semanas
- **Archivo afectado**: Nuevo `SelfHealingService.java`, modificación a `ReplicationService.java`

**Qué es**: Si un nodo muere o un share se corrompe, fragmentiX lo detecta y reconstruye automáticamente. Rain Drops pierde datos permanentemente.

**Cómo funciona**:
1. Cada storage node hace heartbeat cada 30s
2. Si un nodo no responde, los K nodos restantes reconstruyen el share perdido
3. Usan los K shares disponibles → interpolación de Lagrange → share original
4. El share reconstruido se almacena en un nodo de backup (o en el nodo recuperado cuando vuelve)
5. Requiere VSS para verificar que el share reconstruido es correcto

**Implementación**:
- Heartbeat entre nodos: POST /heartbeat cada 30s
- Monitoreo de nodos:谁 no responde → trigger self-healing
- Reconstrucción distribuida: nodos cooperan para recrear share
- Persistencia del share reconstruido
- Integración con VSS (IMP-001) para verificación

**Impacto**: Resiliencia paritaria con fragmentiX. Datos sobreviven a la caída de nodos.

---

### IMP-006: Configuración Dinámica (Hot Reload)
- **Estado**: ❌ No implementado (solo env vars)
- **Prioridad**: ALTA
- **Esfuerzo**: ~150 líneas Java, 1 semana
- **Archivo afectado**: Nuevo `DynamicConfig.java`, modificación a controllers

**Qué es**: fragmentiX puede reconfigurar ubicaciones de storage sin reiniciar. Rain Drops requiere rebuild de Docker y cambio de env vars.

**Implementación**:
- Endpoint `PUT /admin/config` con nueva configuración:
```json
{
  "peers": ["keeper-1:8080", "keeper-2:8080", "keeper-5:8080"],
  "apiKey": "nueva-clave",
  "rateLimits": { "POST": 200, "GET": 400 }
}
```
- Configuración persistida en archivo JSON o H2
- Health check reporta config version y last-modified
- Hot reload sin restart de Spring Boot (usando @RefreshScope o custom)
- Backup automático de config anterior

**Impacto**: UX operacional. Reconfigurar un cluster sin downtime.

---

## NIVEL 3 — MEDIA (Futuro-proofing)

### IMP-007: Post-Quantum Hybrid Mode (CRYSTALS-Kyber)
- **Estado**: ❌ No implementado
- **Prioridad**: MEDIA
- **Esfuerzo**: ~250 líneas Java + dependencia Bouncy Castle PQC, 2 semanas
- **Archivo afectado**: Nuevo `HybridQuantumScheme.java`

**Qué es**: AES-256-GCM es resistente a quantum (Grover reduce a 128-bit, aún seguro), pero Shamir SSS sobre GF(2⁵²¹⁻¹) no tiene protección formal post-quantum contra lattice attacks. NIST MPTC busca esquemas híbridos.

**Implementación**:
- Kyber-768 (NIST PQC standard) para key encapsulation
- Flujo: masterKey → Kyber encapsulate → ciphertextKyber + encryptedKey
- El ciphertextKyber se almacena en RainMap
- En reconstruct: Kyber decapsulate → masterKey → SSS combine
- Dual protection: SSS + PQC

**Impacto**: Futuro-proofing. Preparado para cuando las computadoras cuánticas amenacen SSS.

---

### IMP-008: Compliance Reports (SOC2, NIS2, ISO 27001)
- **Estado**: ❌ No implementado
- **Prioridad**: MEDIA
- **Esfuerzo**: ~200 líneas Java, 2 semanas
- **Archivo afectado**: Nuevo `ComplianceService.java`

**Qué es**: fragmentiX se vende a gobiernos europeos porque genera reports de compliance. Rain Drops tiene audit logs pero no reports formales.

**Implementación**:
- Endpoint `GET /admin/compliance/audit-log?from=&to=` — export audit trail
- Endpoint `GET /admin/compliance/nis2` — reporte NIS2 (data distribution, encryption status)
- Endpoint `GET /admin/compliance/health-report` — estado de todos los nodos
- Export a PDF/CSV
- Timestamped con blockchain anchoring (opcional)

**Impacto**: Enterprise adoption. Sin compliance reports, no vendes a gobiernos ni grandes empresas.

---

### IMP-009: RBAC (Role-Based Access Control)
- **Estado**: ❌ No implementado (solo API key simple)
- **Prioridad**: MEDIA
- **Esfuerzo**: ~300 líneas Java, 2 semanas
- **Archivo afectado**: `storage/SecurityConfig.java`, nuevo `RBACService.java`

**Qué es**: Solo hay API key binaria (accede o no). No hay roles como "admin puede store+retrieve, user solo retrieve".

**Implementación**:
- Roles: `admin`, `writer`, `reader`, `viewer`
- Permisos por endpoint:
  - admin: todo
  - writer: POST /drops, POST /rainmaps
  - reader: GET /drops, GET /rainmaps
  - viewer: GET /health, GET /dashboard
- JWT tokens con roles embebidos
- Persistencia en H2 (tabla `users`)

**Impacto**: Multi-usuario. Sin RBAC, solo un operador puede usar el sistema.

---

### IMP-010: Benchmark Suite y Comparativa
- **Estado**: ❌ No implementado
- **Prioridad**: MEDIA
- **Esfuerzo**: ~200 líneas Java/Python, 1 semana
- **Archivo afectado**: Nuevo directorio `benchmark/`

**Qué es**: No hay métricas de rendimiento publicadas. Sin benchmarks, no puedes demostrar que Rain Drops es mejor (o más rápido) que fragmentiX o shard.

**Implementación**:
- Script de benchmark: store throughput (ops/sec), retrieve latency (ms)
- Comparativa contra: fragmentiX (si disponible), shard (Rust), plain S3
- Métricas: CPU, memoria, disco, red
- Reporte automático con gráficas
- GitHub Actions para CI/CD benchmark

**Impacto**: Credibilidad técnica. Los adoptadores quieren números, no solo features.

---

## Resumen de Priorización

| # | Feature | Nivel | Esfuerzo | Impacto Negocio |
|---|---------|-------|----------|----------------|
| IMP-001 | VSS (Feldman) | CRÍTICO | 1-2 sem | Seguridad base |
| IMP-002 | Proactive Refresh | CRÍTICO | 2 sem | Seguridad temporal |
| IMP-003 | S3 Compatibility | ALTA | 3-4 sem | **Abre ecosistema S3** |
| IMP-004 | JS/TS SDK | ALTA | 2 sem | Ecosistema web |
| IMP-005 | Self-Healing | ALTA | 3 sem | Resiliencia |
| IMP-006 | Hot Reload | ALTA | 1 sem | UX operacional |
| IMP-007 | PQC Hybrid | MEDIA | 2 sem | Futuro-proofing |
| IMP-008 | Compliance Reports | MEDIA | 2 sem | Enterprise |
| IMP-009 | RBAC | MEDIA | 2 sem | Multi-usuario |
| IMP-010 | Benchmark Suite | MEDIA | 1 sem | Credibilidad |

**Total estimado**: ~20-22 semanas de desarrollo (1 persona dedicada)

**Orden recomendado de implementación**:
1. IMP-001 + IMP-002 (seguridad base, 3-4 sem)
2. IMP-003 (S3 layer, 3-4 sem) ← EL QUE MÁS Vende
3. IMP-004 (JS SDK, 2 sem)
4. IMP-005 (Self-healing, 3 sem)
5. IMP-006 (Hot reload, 1 sem)
6. IMP-010 (Benchmarks, 1 sem)
7. IMP-007 (PQC, 2 sem)
8. IMP-008 (Compliance, 2 sem)
9. IMP-009 (RBAC, 2 sem)

---

## Bug conocido: RainMap.create() k=n

- **Archivo**: `raindrops-core/RainMap.java` línea ~98
- **Problema**: `int k = n;` hardcodeado
- **Fix**: Cambiar `create(drops, urls)` a `create(drops, urls, k)`
- **Impacto**: Todos los RainMaps se crean con threshold máximo
- **Prioridad**: FIX INMEDIATO (antes de cualquier mejora)

---

## Comparativa Final: Rain Drops (mejorado) vs fragmentiX

| Feature | fragmentiX | Rain Drops (actual) | Rain Drops (mejorado) |
|---------|-----------|---------------------|----------------------|
| Open Source | ❌ | ✅ | ✅ |
| VSS | ✅ | ❌ | ✅ IMP-001 |
| Proactive Refresh | ✅ | ❌ | ✅ IMP-002 |
| S3 Compatible | ✅ | ❌ | ✅ IMP-003 |
| JS/TS SDK | ❌ | ❌ | ✅ IMP-004 |
| Self-Healing | ✅ | ❌ | ✅ IMP-005 |
| Hot Reload | ✅ | ❌ | ✅ IMP-006 |
| PQC Hybrid | ✅ | ❌ | ✅ IMP-007 |
| Compliance | ✅ | ❌ | ✅ IMP-008 |
| RBAC | ✅ | ❌ | ✅ IMP-009 |
| Hardware Appliance | ✅ ($$$) | ❌ | ❌ (ventaja: $0) |
| GF(2⁵²¹⁻¹) | ❌ GF(256) | ✅ | ✅ |
| TTL Expiry | ❌ | ✅ | ✅ |
| HMAC per drop | ❌ | ✅ | ✅ |
| Witness Node | ❌ | ✅ | ✅ |
| Costo | $5,000+ | $0 | $0 |

**Con todas las mejoras, Rain Drops tendría feature parity con fragmentiX + ventajas únicas (open source, GF grande, TTL, witness, costo $0).**
