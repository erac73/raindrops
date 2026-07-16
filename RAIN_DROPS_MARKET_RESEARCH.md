# RAIN DROPS — Investigación de Mercado y Competencia
> Última actualización: 2026-07-15
> Autor: Edwar Antonio Ramírez Castillo (erac73)

---

## 1. El Mercado de Criptografía Umbral

### Tamaño y Crecimiento

| Mercado | 2025 | Proyección | CAGR | Fuente |
|---------|------|-----------|------|--------|
| Threshold Cryptography | $2.1B | $9.4B (2034) | 18.4% | MarketIntelo |
| Cryptography General | $13.16B | $37.88B (2032) | 16.3% | 360iResearch |
| Crypto Custody | $2.1B | $27.2B (2030) | 66.7% | Business Research Co |
| Enterprise Key Management | $4.18B | $20.47B (2034) | 19.3% | Insight Partners |
| Blockchain Identity | $0.52B | $80.81B (2035) | 65.6% | Market Research Future |

### Drivers del Mercado

1. **Regulación NIST**: Primera convocatoria oficial de Threshold Cryptography publicada en enero 2026 (NIST IR 8214C). ISO estandarizando esquemas threshold. La NIST busca implementaciones de referencia.

2. **Directiva NIS2 (EU)**: Exige sovereign data storage, dual-control y split-knowledge para empresas europeas. Multas por incumplimiento.

3. **Ransomware**: $4.4M promedio por breach global, $10.22M en EEUU (IBM 2025). Los incidentes detectados internamente ahorran ~$900K.

4. **Quantum threat**: Ataques "store now, decrypt later" — los datos deben estar protegidos HOY contra computadoras cuánticas futuras. BSI (Alemania), ENISA y NIST recomiendan migración a PQC.

5. **Institutional crypto**: $350B+ en custodia institucional (Coinbase). Crypto custody crece 67% anual. Multi-sig y MPC son estándar.

6. **Cloud sovereignty**: 70% de cloud services son americanos y están sujetos al CLOUD Act. Europa y Asia buscan alternativas.

---

## 2. Productos y Empresas Competidoras

### A. Empresas Directamente Competidoras

#### fragmentiX (Austria, 2018) — EL MÁS PARECIDO A RAIN DROPS

- **Qué hace**: Almacenamiento distribuido basado en Secret Sharing
- **Productos**:
  - fragmentiX ONE: Appliance de escritorio para SMEs, soporta 8 ubicaciones distribuidas
  - fragmentiX CLUSTER: Enterprise-grade, soporta hasta 26 ubicaciones. Hardware DELL.
  - fragmentiX QBackup: Backup quantum-safe
  - fragmentiX as a Service (próximamente)
- **Tecnología**: Secret Sharing + AES + distribución a ubicaciones S3/NFS/Azure
- **Seguridad**: Information-Theoretic Security (ITS) — no depende de suposiciones computacionales
- **Clientes**: Gobierno austriaco, EU Horizon 2020, sector salud, proyectos SAGA-1G
- **Equipo**: ~11 empleados, Klosterneuburg, Austria
- **Oficinas**: Austria, Suiza, Canadá (en registro)
- **Financiamiento**: Privado, sin funding público conocido
- **Website**: fragmentix.com

**Comparación con Rain Drops:**
| Aspecto | fragmentiX | Rain Drops |
|---------|-----------|------------|
| Open Source | ❌ Comercial cerrado | ✅ MIT License |
| Form factor | Hardware appliance ($$$) | Software, corre en Pi5 ($50) |
| Storage locations | 3-26 | 3-N (ilimitado) |
| Field | GF(256) typical | GF(2⁵²¹⁻¹) |
| Witness/Coordinator | ❌ | ✅ Stateless |
| TTL expiry | ❌ | ✅ Drops expiran |
| HMAC por drop | ❌ | ✅ |
| Self-healing | ✅ Auto-repair | ❌ Pendiente |
| P2P replication | ❌ (S3-based) | ✅ Async |
| REST API | S3 proxy | REST nativo |
| Costo | Miles de USD | Gratis (open source) |

#### Vault12 (EEUU, 2015)

- **Qué hace**: Backup e inheritance de crypto wallets usando SSS
- **Producto**: Vault12 Guard (app iOS/Android)
- **Tecnología**: Shamir Secret Sharing + red P2P de "Guardians" + Secure Enclave
- **Financiamiento**: $10M Series A de JP Morgan
- **Equipo**: Fundadores de Voltage Security (adquirida por HP 2015)
- **Website**: vault12.com

**Comparación con Rain Drops:**
| Aspecto | Vault12 | Rain Drops |
|---------|---------|------------|
| Foco | Crypto keys/seeds | Datos generales distribuidos |
| Platform | App móvil | Distributed server system |
| Open Source | ❌ | ✅ |
| Inheritance | ✅ Core feature | ❌ |
| Storage nodes | ❌ (Guardian network) | ✅ |

#### ShamirVault (web tool)

- **Qué hace**: Herramienta web para split de seed phrases de Bitcoin
- **Tecnología**: GF(256), 100% browser-side, PWA offline
- **Diferencia**: Solo un tool, no un sistema distribuido. Sin storage nodes.

### B. Empresas de Custodia Crypto (mercado adyacente)

| Empresa | Custodia | Tecnología | Año fund. |
|---------|----------|-----------|-----------|
| Coinbase | $350B+ AUC | MPC signatures | 2012 |
| BitGo | Multisig wallets | Threshold sigs (TSS) | 2013 |
| Anchorage Digital | Banco federal OCC | MPC | 2017 |
| Fidelity Digital Assets | BTC, ETH, LTC, SOL | Custodia tradicional | 2018 |
| Copper.co | ClearLoop settlement | MPC | 2018 |
| Ripple/Metaco | Banking custody | HSM + MPC | 2023 (adquisición) |

### C. Plataformas MPC/Threshold

| Proyecto | Tipo | Descripción | Funding |
|----------|------|-------------|---------|
| Web3Auth | WaaS | Wallet-as-a-Service usando SSS + TSS | $13M+ |
| Lit Protocol | Network | Distributed key management para Web3 | $10M+ |
| Threshold Network | Blockchain | tBTC, threshold signatures (T token) | DAO |
| Fireblocks | Enterprise | MPC custody para institutions | $1B+ |
| Cubist Inc. | Key mgmt | Non-custodial key management Web3 | Reciente |

### D. Investigación Académica

| Proyecto | Institución | Año | Descripción |
|----------|------------|-----|-------------|
| Thetacrypt | Univ. de Bern | 2025 | Biblioteca versatile con 6 esquemas threshold, agnóstica a lenguaje |
| Efficient Secret Sharing | ACM CCS 2024 | 2024 | Nuevo framework 8-25x más rápido que Shamir para reconstruct |
| NIST MPTC | NIST | 2026 | Estandarización — convocatoria abierta enero 2026 |
| Share Refreshing | IACR ePrint 2025/277 | 2025 | Refresh de shares + reconstruction policies expresivas |
| DKG with Expressive Policies | Trento/FBK | 2025 | Key recovery distribuido con árboles de acceso |

---

## 3. Proyectos Open Source Similares

### Repositorios GitHub relevantes:

| Proyecto | Lenguaje | Estrellas | Descripción | Similitud con Rain Drops |
|----------|----------|-----------|-------------|-------------------------|
| 10d9e/shard | Rust | 5 | Red P2P decentralized con SSS + proactive refresh | ALTA — red distribuida con refresh de shares |
| dsprenkels/sss | C | 406 | Library SSS resistente a side-channel | MEDIA — solo library, sin storage |
| vault12/capacitor-shamir | Multi | — | SSS nativo iOS/Android/Web | BAJA — solo crypto |
| enVinci/shamir-mnemonic | Python | — | SLIP-0039 SSS para mnemonics | BAJA — solo crypto |
| SpinResearch/RustySecrets | Rust | — | Threshold SSS en Rust | BAJA — solo library |
| katvio/fractum | — | — | File encryption + split into shares | MEDIA — file-oriented |
| paritytech/banana_split | Python | — | SSS para personas con amigos | BAJA — simple tool |
| 10d9e/shard | Rust | 5 | Decentralized threshold network + proactive refresh | ALTA |

### Comparación técnica:

| Feature | Rain Drops | shard (Rust) | sss (C) | fragmentiX |
|---------|-----------|-------------|---------|-----------|
| Distributed storage | ✅ | ✅ | ❌ | ✅ |
| P2P replication | ✅ | ✅ | ❌ | ❌ (S3) |
| Witness/coordinator | ✅ | ❌ | ❌ | ❌ |
| AES-GCM encryption | ✅ | ❌ | ❌ | ✅ |
| HMAC integrity | ✅ | ❌ | MAC | ❌ |
| TTL expiry | ✅ | ❌ | ❌ | ❌ |
| Proactive refresh | ❌ | ✅ | ❌ | ✅ |
| REST API | ✅ | ❌ | CLI | S3 |
| Docker support | ✅ | ✅ | ❌ | Appliance |
| Open source | ✅ MIT | ✅ | ✅ MIT | ❌ |
| Field size | GF(2⁵²¹⁻¹) | — | GF(2⁸) | GF(256) |
| Spring Boot | ✅ | ❌ | ❌ | ❌ |

---

## 4. Aplicaciones Reales del Negocio

### 10 mercados donde se usa SSS/Threshold hoy:

1. **Custodia de criptomonedas**
   - Wallets (Ledger, Trezor usan SSS para backup)
   - Exchanges (Coinbase, BitGo usan MPC/threshold)
   - ETFs (80%+ de spot BTC ETFs en Coinbase custody)

2. **Password managers**
   - 1Password: SSS para master key distribution
   - Bitwarden: Secret Sharing entre miembros

3. **Certification Authorities**
   - Distribute root CA keys entre múltiples HSMs
   - Ningún operador individual puede comprometer la CA

4. **Cloud storage sovereignty**
   - fragmentiX vende a gobiernos europeos
   - NIS2 compliance exige distribución de datos
   - CLOUD Act motiva a EU/Asia a buscar alternativas

5. **Healthcare data**
   - Datos médicos distribuidos entre instituciones
   - Research data sharing con privacy
   - EU Horizon 2020 financia proyectos de este tipo

6. **Government/military**
   - Nuclear launch codes (caso clásico)
   - Classified data distribution
   - Secret clearance management

7. **Federated learning**
   - Secure aggregation entre múltiples instituciones
   - Paper ACM CCS 2024: SSS reduce 22% costo computacional
   - Google, Apple usan MPC para federated learning

8. **IoT security**
   - Key management en dispositivos distribuidos
   - Device provisioning sin trusted dealer
   - Edge computing con threshold signing

9. **Disaster recovery**
   - Backup distribuido anti-ransomware
   - Geo-redundancy sin confiar en un proveedor
   - Compliance con 3-2-1 backup rule

10. **Digital inheritance**
    - Vault12: crypto inheritance
    - ShamirVault: seed phrase backup
    - Legal frameworks emergentes para crypto inheritance

---

## 5. Análisis FODA de Rain Drops

### Fortalezas
- **Open source** — Único sistema distributed storage con SSS que es completamente libre
- **Witness node** — Arquitectura innovadora: coordinador stateless que nadie más tiene
- **GF(2⁵²¹⁻¹)** — Field más grande que la mayoría (GF(256)), mayor seguridad
- **HMAC-SHA256 por drop** — Verificación de integridad atómica
- **TTL expiry** — Drops expiran automáticamente, irrecoverables
- **Multi-format**: REST API + SDK Java + Python reference implementation
- **Docker-ready**: Corre en cualquier plataforma con Docker
- **Self-hosted**: Sin dependencia de servicios externos

### Debilidades
- **No hay tests de integración** para storage y witness modules
- **Bug RainMap.k=n** no está fixeado (hardcoded)
- **Sin cliente móvil** (solo REST API/SDK)
- **Sin proactive refresh** de shares (fragmentiX y shard sí lo tienen)
- **Sin self-healing** de shares corruptos
- **Sin Verifiable Secret Sharing (VSS)** — no hay forma de verificar shares honestos
- **Documentación limitada** para adoptadores externos
- **Equipo pequeño** (1 persona)

### Oportunidades
- **NIST MPTC**: Convocatoria abierta para implementaciones threshold — Rain Drops podría contribuir
- **NIS2 compliance**: Empresas europeas necesitan sovereign storage alternativo a fragmentiX
- **Latam**: Sin equivalente a NIS2, pero crecimiento en data privacy laws
- **SME backup**: fragmentiX es caro ($5K+). Rain Drops en Pi5 es alternativa de $50
- **Crypto inheritance**: Vault12 es closed source. Alternativa open sería valiosa
- **Federated learning**: Paper demuestra 22% savings con SSS en secure aggregation
- **Academic**:GF(2⁵²¹⁻¹) es más fuerte que GF(256) — ventaja diferenciadora

### Amenazas
- **fragmentiX** ya tiene tracción (gobierno, EU projects)
- **Vault12** tiene funding significativo ($10M JP Morgan)
- **Thetacrypt** (Univ. de Bern) es una biblioteca threshold más completa
- **NIST** podría estandarizar esquemas que no usan Shamir clásico
- **Quantum computing** podría romper AES (aunque AES-256 es resistente)
- **Complejidad**: Adoptar un sistema distributed storage es más difícil que un tool

---

## 6. Roadmap Sugerido (Priorizado por Impacto de Negocio)

### Corto plazo (1-3 meses) — Credibilidad
1. Fix RainMap.create() bug
2. Agregar VSS (Verifiable Secret Sharing) — Feldman's scheme
3. Tests de integración completos para storage y witness
4. Publicar en Maven Central + Docker Hub
5. Contribuir a NIST MPTC con implementación de referencia

### Mediano plazo (3-6 meses) — Diferenciación
1. Proactive share refresh (como shard y fragmentiX)
2. Client SDK: Python, JavaScript/TypeScript
3. Dashboard web mejorado con métricas en tiempo real
4. Benchmark de rendimiento vs fragmentiX y shard
5. Documentación para adoptores (guía de instalación, API docs)

### Largo plazo (6-12 meses) — Escala
1. Cliente móvil (Android/iOS) para gestión de shares
2. Integración con cloud providers (S3, Azure Blob, GCS)
3. Hardware appliance version (Raspberry Pi optimizado)
4. Enterprise features: audit logs, RBAC, compliance reports
5. Multi-tenant support
6. Post-quantum hybrid: SSS + PQC (CRYSTALS-Kyber/Dilithium)

---

## 7. Referencias

### Investigación académica
- Barbaraci et al. "Thetacrypt: A Distributed Service for Threshold Cryptography" (arXiv 2502.03247, Feb 2025)
- "Efficient Secret Sharing for Large-Scale Applications" (ACM CCS 2024)
- Montanari et al. "Tighter Control for DKG: Share Refreshing and Expressive Reconstruction Policies" (IACR 2025/277)
- NIST IR 8214C: "First Call for Multi-Party Threshold Schemes" (Jan 2026)

### Estándares
- NIST Multi-Party Threshold Cryptography (MPTC): csrc.nist.gov/projects/threshold-cryptography
- ISO/IEC 18033 (Encryption)
- NIS2 Directive (EU cybersecurity)

### Mercado
- MarketIntelo: Threshold Cryptography Market Report 2034
- IBM Cost of a Data Breach Report 2025
- Business Research Company: Crypto Custody Provider Market Report 2026
- Insight Partners: Enterprise Key Management Market 2025-2034

### Competidores
- fragmentix.com — Quantum-safe distributed storage (Austria)
- vault12.com — Crypto inheritance and backup (EEUU)
- shamirvault.com — Browser-based SSS tool
- github.com/10d9e/shard — Decentralized threshold network (Rust)
- github.com/dsprenkels/sss — SSS library (C, 406 stars)
