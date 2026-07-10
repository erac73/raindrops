# Rain Drops

> *"Una gota de lluvia no contiene información sobre la tormenta."*

**Autor:** Edwar Antonio Ramírez Castillo  
**Estado:** Fase 3 — Nodo Testigo (Witness Node) <img src="https://img.shields.io/badge/status-alpha-yellow?style=flat-square" alt="Alpha"/>

---

## ¿Qué es Rain Drops?

**Rain Drops** es un modelo de almacenamiento distribuido de información basado en **criptografía de umbral**. Los datos se fragmentan en micro-unidades criptográficas llamadas **gotas (drops)** que, individualmente, no tienen ningún significado. Los datos originales solo pueden reconstruirse cuando un número suficiente de gotas convergen bajo condiciones verificadas.

---

## Arquitectura

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  RainClient │────▶│ Nodo Testigo │────▶│ Nodo Almacén │
│   (SDK)     │     │ (Witness)    │     │   keeper-1   │
└─────────────┘     │              │     └──────────────┘
                    │  /witness/   │     ┌──────────────┐
                    │  store       │────▶│ Nodo Almacén │
                    │  /witness/   │     │   keeper-2   │
                    │  reconstruct │     └──────────────┘
                    │  /witness/   │     ┌──────────────┐
                    │  verify      │────▶│ Nodo Almacén │
                    └──────────────┘     │   keeper-3   │
                                         └──────────────┘
```

| Componente | Descripción |
|---|---|
| **RainClient SDK** | Librería Java para operaciones DROP/RECONSTRUCT. Coordina con nodos de almacenamiento o testigo. |
| **Nodo Testigo (Witness)** | Coordinador sin estado que verifica la integridad de las gotas (MAC, TTL) antes de RECONSTRUCT. Previene que nodos maliciosos inyecten datos falsos. |
| **Nodo de Almacenamiento** | Servicio Spring Boot que almacena gotas y RainMaps. Replica a pares. Reaper de TTL. |

Todos los nodos exponen:
- Swagger UI en `/swagger-ui.html`
- OpenAPI spec en `/api-docs`
- Métricas Prometheus en `/actuator/prometheus`
- Health en `/health`

---

## Novedades de Fase 3

### Nodo Testigo (Witness Node)
- **Verificación de gotas**: `POST /witness/verify` — verifica MAC y TTL de un drop individual
- **Almacenamiento coordinado**: `POST /witness/store` — fragmenta datos, distribuye drops a nodos de almacenamiento, retorna RainMapId + masterKey
- **Reconstrucción verificada**: `POST /witness/reconstruct` — recolecta gotas, verifica cada una, reconstruye datos solo si todas pasan verificación

### Autenticación API Key
- Los nodos de almacenamiento pueden protegerse con API Key vía variable de entorno `API_KEY`
- Las peticiones deben incluir cabecera `X-API-Key`
- Endpoints públicos (health, swagger, dashboard) permanecen abiertos

### Replicación de RainMaps
- Los RainMaps ahora se replican automáticamente a todos los pares, igual que los drops
- Garantiza disponibilidad del RainMap incluso si el nodo original falla

### Imágenes Multi-arch
- Build automático para `linux/amd64` y `linux/arm64` (Raspberry Pi)
- Almacenamiento: `Dockerfile.storage`
- Testigo: `Dockerfile.witness`

---

## Inicio Rápido

### Requisitos
- Java 17+, Maven 3.8+, Docker & Docker Compose

### Compilar y probar
```bash
mvn -f raindrops-fase1/raindrops/pom.xml install
mvn -f raindrops-fase1/storage/pom.xml test
```

### Despliegue local con Docker
```bash
API_KEY=mi-clave-secreta docker compose -f docker-compose.yml up -d --build
```

### Usar RainClient SDK
```xml
<dependency>
    <groupId>io.raindrops</groupId>
    <artifactId>raindrops-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.raindrops.client.RainClient;

List<String> nodes = List.of("http://localhost:9081", "http://localhost:9082", "http://localhost:9083");
try (RainClient client = new RainClient(nodes)) {
    String rainMapId = client.store("Hola Rain Drops!".getBytes(), 5, 3, 30);
    byte[] data = client.retrieve(rainMapId, masterKey);
}
```

### Usar API del Nodo Testigo

```bash
# Almacenar
curl -X POST http://localhost:9080/witness/store \
  -H "Content-Type: application/json" \
  -d '{"data": "'$(echo -n "Mi dato secreto" | base64)'", "n": 5, "k": 3, "ttlDays": 30}'

# Reconstruir
curl -X POST http://localhost:9080/witness/reconstruct \
  -H "Content-Type: application/json" \
  -d '{"rainMapId": "<id>", "masterKeyHex": "<key>"}'

# Verificar
curl -X POST http://localhost:9080/witness/verify \
  -H "Content-Type: application/json" \
  -d '{"dropJson": "<json>", "masterKeyHex": "<key>"}'
```

---

## Publicaciones

> Ramírez Castillo, E. A. (2026). *Rain Drops: Un Modelo Teórico de Almacenamiento Distribuido de Información Basado en Criptografía de Umbral y Fragmentación Semántica.*

> Ramírez Castillo, E. A. (2026). *Rain Drops — Fase 1: Implementación del Núcleo Criptográfico.*

---

## Licencia

Este proyecto se publica bajo la **Licencia MIT**.

---

*Rain Drops — Edwar Antonio Ramírez Castillo, 2026*
