# Rain Drops

> *"Una gota de lluvia no contiene información sobre la tormenta."*

**Autor:** Edwar Antonio Ramírez Castillo  
**Estado:** Fase 1 — Núcleo Criptográfico <img src="https://img.shields.io/badge/status-done-success?style=flat-square" alt="Completado"/> | Fase 2 — Nodo de Almacenamiento <img src="https://img.shields.io/badge/status-next-yellow?style=flat-square" alt="Siguiente"/>

---

## ¿Qué es Rain Drops?

**Rain Drops** es un modelo de almacenamiento distribuido de información basado en **criptografía de umbral**. Los datos se fragmentan en micro-unidades criptográficas llamadas **gotas (drops)** que, individualmente, no tienen ningún significado. Los datos originales solo pueden reconstruirse cuando un número suficiente de gotas convergen bajo condiciones verificadas.

La metáfora es precisa: así como una sola gota de lluvia no contiene información sobre la tormenta, cada gota es, en aislamiento, indistinguible de ruido aleatorio. Solo cuando se combinan **K de N gotas** emerge el dato original.

Esto **no** es cifrado superpuesto sobre almacenamiento. **La confidencialidad es una propiedad estructural del modelo.**

---

## Conceptos Fundamentales

| Concepto | Descripción |
|---|---|
| **Gota (Drop)** | Micro-unidad criptográfica: `(id, x, y, mac, ttl)`. Individualmente sin significado. |
| **Lluvia (Rain)** | Conjunto de N gotas generadas a partir de un dato, que requiere K para reconstruirse. |
| **Mapa de Lluvia (Rain Map)** | Índice cifrado que asigna IDs de gotas a URLs de nodos de almacenamiento. |
| **Umbral K** | Mínimo de gotas necesarias para reconstruir. Con K-1 gotas: información cero. |
| **Nodo Testigo (Witness Node)** | Coordinador sin estado que orquesta la reconstrucción. |
| **Oráculo (Oracle)** | Servicio externo que firma condiciones de acceso (tiempo, identidad, multifirma). |

---

## Propiedades de Seguridad

| Propiedad | Garantía |
|---|---|
| **Secreto perfecto** | Con menos de K gotas, el secreto está oculto information-theoretically — no solo computacionalmente. |
| **IND-CCA2** | El esquema híbrido (AES-256-GCM + SSS) es seguro contra ataques de texto cifrado elegido. |
| **Integridad** | Cada gota lleva un HMAC-SHA256. La manipulación se detecta con probabilidad 1 − 2⁻²⁵⁶. |
| **Identidad ciega** | Los IDs de las gotas son `HMAC(nonce, masterKey)` — sin correlación visible con los datos ni entre sí. |
| **Expiración temporal** | Las gotas tienen un TTL. Las gotas expiradas son irrecuperables por diseño. |

---

## Arquitectura

```
RainDropsCore          ← punto de entrada único: DROP / RECONSTRUCT
    │
    ├── ShamirSSS      ← esquema (K,N)-umbral sobre GF(2^521-1)
    │       └── Interpolación Lagrange, evaluación Horner
    │
    ├── HybridScheme   ← AES-256-GCM (Bouncy Castle 1.77)
    │
    └── DropFactory    ← HMAC-SHA256, identidad ciega, TTL
            └── Drop   ← estructura de datos inmutable
```

### Cómo funciona DROP (fragmentación)

```
Datos D
  │
  ├─ si |D| ≤ 65 bytes ──→ S = D (modo directo)
  │
  └─ si |D| > 65 bytes ──→ k_AES = clave aleatoria de 256 bits
                            C = AES-256-GCM(k_AES, D)   ← almacenar en cualquier lado
                            S = k_AES                    ← fragmentar con SSS
  │
  ▼
f(x) = S + a₁x + ... + a_{K-1}x^{K-1}  mod p     ← polinomio aleatorio
  │
  ├── drop_1 = (HMAC(nonce₁, mk), 1, f(1), MAC, ttl)  → Nodo 1
  ├── drop_2 = (HMAC(nonce₂, mk), 2, f(2), MAC, ttl)  → Nodo 2
  │   ...
  └── drop_N = (HMAC(nonceN, mk), N, f(N), MAC, ttl)  → Nodo N

Retorna: (RainMap sellado con AES-GCM, masterKey)
```

### Cómo funciona RECONSTRUCT (reconstrucción)

```
Abrir Rain Map → verificar política de acceso → emitir token de quórum
  │
  ▼
Recolectar K gotas en paralelo → verificar cada MAC → verificar TTL
  │
  ▼
Interpolación Lagrange: S = Σᵢ yᵢ · Lᵢ(0)  mod p
  │
  ├─ modo directo ──→ D = S
  └─ modo híbrido ──→ D = AES-256-GCM-Decrypt(S, C)
  │
  ▼
Borrar S y gotas de memoria → retornar D
```

---

## Estructura del Proyecto

```
raindrops-fase1/
├── README.md               ← Documentación principal (inglés)
├── README_ES.md            ← Esta documentación (español)
├── LICENSE                 ← Licencia MIT
├── .gitignore              ← Archivos ignorados por git
├── assets/
│   └── raindrops-logo.svg  ← Logotipo del proyecto
│
└── raindrops/
    ├── pom.xml
    ├── raindrops.py         ← Implementación en Python (demo/node simulator)
    └── src/
        ├── main/java/io/raindrops/core/
        │   ├── ShamirSSS.java        ← SSS sobre GF(2^521-1)
        │   ├── Drop.java             ← estructura de gota inmutable
        │   ├── DropFactory.java      ← creación + verificación HMAC
        │   ├── HybridScheme.java     ← AES-256-GCM
        │   └── RainDropsCore.java    ← fachada DROP / RECONSTRUCT
        ├── main/java/io/raindrops/demo/
        │   └── RainDropsDemo.java    ← demo interactiva CLI
        └── test/java/io/raindrops/core/
            ├── ShamirSSSTest.java    ← 12 tests
            ├── HybridSchemeTest.java ← 5 tests
            └── RainDropsCoreTest.java← 12 tests
```

---

## Primeros Pasos

### Requisitos

- Java 17+
- Maven 3.8+
- Python 3.9+ (para la demo en Python)

### Compilar y probar (Java)

```bash
cd raindrops
mvn test
```

Salida esperada:

```
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Demo interactiva (Java)

```bash
cd raindrops
mvn compile exec:java -Dexec.mainClass="io.raindrops.demo.RainDropsDemo"
```

### Demo en Python

```bash
cd raindrops
python raindrops.py
```

### Ejemplo rápido (Java)

```java
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainDropsCore.RainResult;

// Fragmentar datos en 7 gotas, requiriendo 3 para reconstruir (TTL de 1 año)
byte[] data    = "Registro médico confidencial".getBytes();
RainResult rain = RainDropsCore.drop(data, 7, 3, 365);

// Reconstruir usando solo 3 de las 7 gotas
byte[] recovered = RainDropsCore.reconstruct(
    rain.getDrops().subList(0, 3),
    rain.getMasterKey(),
    rain.getCiphertext(),
    rain.getK(),
    rain.isDirectMode()
);

System.out.println(new String(recovered));
// Registro médico confidencial
```

---

## Implementación en Python

Además de la implementación en Java, el proyecto incluye una implementación completa en Python (`raindrops.py`) que sirve como:

- **Prototipo funcional**: Implementación completa del modelo con cifrado AES-GCM
- **Simulador de red**: Incluye `KeeperNode` — nodos simulados que almacenan y entregan gotas
- **Perfiles por tipo de dato**: Parámetros N/K predefinidos según el tipo (`credential`, `document`, `health`, `media`)
- **Demostración interactiva**: Ejecuta casos de uso reales (credenciales, historiales médicos)

La versión en Python usa el primo 257 (GF(257)) para trabajar cómodamente con bytes individuales, mientras que la versión en Java usa el primo de Mersenne 2^521−1 para máxima seguridad.

---

## Cobertura de Tests

| Clase de test | Tests | ¿Qué verifica? |
|---|---|---|
| `ShamirSSSTest` | 21 | Correctitud matemática, secreto perfecto, conversión de bytes, validación |
| `HybridSchemeTest` | 6 | Cifrado/descifrado, propiedad IND, detección de manipulación, limpieza de clave |
| `RainDropsCoreTest` | 14 | Flujo completo, resiliencia N-K, integridad, TTL, independencia entre lluvias |
| **Total** | **41** | — |

Tests notables:

- **`singleShareRevealsNothing`** — 100 pruebas; ninguna gota individual reconstruye el secreto
- **`toleratesLossOfNMinusK`** — pierde 4 de 7 nodos; reconstruye correctamente con las K=3 restantes
- **`differentRainsAreIndependent`** — verifica que las gotas no se pueden mezclar entre lluvias

---

## Hoja de Ruta

| Fase | Componente | Estado |
|---|---|---|
| **1** | Núcleo criptográfico (SSS, AES-GCM, HMAC, gotas) | <img src="https://img.shields.io/badge/-done-success?style=flat-square" alt="Completado"/> |
| **2** | Nodo de Almacenamiento — API REST (Spring Boot) + SQLite + TTL Reaper | <img src="https://img.shields.io/badge/-next-yellow?style=flat-square" alt="Siguiente"/> |
| **3** | Mapa de Lluvia (Rain Map sellado con AES-GCM) + Nodo Testigo + protocolo de quórum | <img src="https://img.shields.io/badge/-planned-lightgrey?style=flat-square" alt="Planificado"/> |
| **4** | Protocolo Gossip — descubrimiento autónomo de nodos + reputación | <img src="https://img.shields.io/badge/-planned-lightgrey?style=flat-square" alt="Planificado"/> |
| **5** | SDK Cliente — API de alto nivel + políticas declarativas YAML | <img src="https://img.shields.io/badge/-planned-lightgrey?style=flat-square" alt="Planificado"/> |
| **6** | Oráculos — Tiempo, Identidad, Multifirma, HTTP, Compuesto | <img src="https://img.shields.io/badge/-planned-lightgrey?style=flat-square" alt="Planificado"/> |
| **7** | Prueba de Lluvia (Proof of Rain) — pruebas de conocimiento cero sobre datos fragmentados | <img src="https://img.shields.io/badge/-planned-lightgrey?style=flat-square" alt="Planificado"/> |

---

## Limitaciones Conocidas (Fase 1)

- **Ceros finales en modo directo** — datos que terminan en bytes `0x00` pueden truncarse en modo directo (≤65 bytes). Usar modo híbrido o prefijar la longitud.
- **Clave maestra en memoria** — `masterKey` se devuelve directamente en `RainResult`. En el sistema completo vive solo dentro de un Rain Map sellado.
- **BigInteger no constante** — la interpolación de Lagrange usa `BigInteger`, que no es constante en tiempo, potencialmente filtrando información temporal.
- **Entropía SecureRandom** — en contenedores sin RNG de hardware, la salida aleatoria temprana puede ser predecible. Usar `haveged` o un demonio de entropía equivalente.

---

## Publicaciones

Esta implementación se basa en el siguiente artículo teórico:

> Ramírez Castillo, E. A. (2026). *Rain Drops: Un Modelo Teórico de Almacenamiento Distribuido de Información Basado en Criptografía de Umbral y Fragmentación Semántica.* Propuesta teórica.

Artículo de implementación que documenta las decisiones de diseño de la Fase 1, preservación de propiedades de seguridad y resultados de pruebas:

> Ramírez Castillo, E. A. (2026). *Rain Drops — Fase 1: Implementación del Núcleo Criptográfico.* Artículo de implementación.

---

## Licencia

Este proyecto se publica bajo la **Licencia MIT**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
