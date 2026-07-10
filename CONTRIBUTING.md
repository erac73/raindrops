# Contributing to Rain Drops

Thank you for your interest in contributing to Rain Drops! This document provides guidelines and instructions for contributing.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Development Setup](#development-setup)
3. [Project Structure](#project-structure)
4. [Coding Standards](#coding-standards)
5. [Testing](#testing)
6. [Pull Request Process](#pull-request-process)
7. [Building Docker Images](#building-docker-images)

---

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Assume good faith
- No harassment or discrimination

## Development Setup

### Prerequisites

- Java 17+ (Temurin or equivalent)
- Maven 3.8+
- Docker & Docker Compose (for integration tests)
- Git

### Clone and Build

```bash
git clone https://github.com/erac73/raindrops-fase1.git
cd raindrops-fase1

# Build core library (install to local Maven repo)
mvn -f raindrops-fase1/raindrops/pom.xml install -DskipTests

# Build storage module
mvn -f raindrops-fase1/storage/pom.xml package -DskipTests

# Build witness module
mvn -f raindrops-fase1/witness/pom.xml package -DskipTests
```

### Import into IDE

- **IntelliJ IDEA:** File → Open → Select `raindrops-fase1` as root
- **VS Code:** Open `raindrops-fase1/` folder, install Java extension pack
- **Eclipse:** File → Import → Maven → Existing Projects

## Project Structure

```
raindrops-fase1/
├── raindrops/          ← Core crypto (no framework dependencies)
│   └── src/main/java/io/raindrops/core/
├── storage/            ← Storage node (Spring Boot)
│   └── src/main/java/io/raindrops/storage/
├── witness/            ← Witness node (Spring Boot)
│   └── src/main/java/io/raindrops/witness/
├── docs/               ← Documentation and web demo
└── .github/workflows/  ← CI/CD pipelines
```

### Module Responsibilities

| Module | Responsibility |
|---|---|
| `raindrops-core` | All cryptographic logic. No Spring Boot dependencies. Pure Java 17. |
| `raindrops-storage` | REST API for drops and rainmaps. JPA persistence. Peer replication. |
| `raindrops-witness` | Stateless orchestration. Drop verification. Store/reconstruct coordination. |

## Coding Standards

### General

- Java 17 with `--release 17` compilation
- Use `final` for immutable classes and utility classes (private constructor)
- No wildcard imports
- Maximum line length: 120 characters
- Braces on same line (K&R style)

### Naming

- Classes: `PascalCase` (`RainDropsCore`, `DropFactory`)
- Methods: `camelCase` (`storeData`, `verifyOrThrow`)
- Constants: `UPPER_SNAKE_CASE` (`DIRECT_MAX_BYTES`, `NONCE_BYTES`)
- Records: `PascalCase` (`RainResult`, `StoreResult`)

### Security

- All byte array getters must return defensive copies (`.clone()`)
- Zeroize sensitive material after use (`Arrays.fill(arr, (byte) 0)`)
- Use constant-time comparison for MAC verification
- Do not log keys, secrets, or plaintext data
- Use Bouncy Castle primitives, not JCE

### Best Practices

- Prefer records for DTOs and results
- Use utility classes (final + private constructor) for static methods
- Never throw `RuntimeException` directly — use domain-specific exceptions
- Document public API with Javadoc (at minimum: params, return, throws)

## Testing

### Running Tests

```bash
# Core tests
mvn -f raindrops-fase1/raindrops/pom.xml test

# Storage integration tests
mvn -f raindrops-fase1/storage/pom.xml test

# All tests
mvn -f raindrops-fase1/raindrops/pom.xml install
mvn -f raindrops-fase1/storage/pom.xml test
```

### Test Standards

- JUnit 5 (`@Test`, `@ParameterizedTest`)
- AssertJ for fluent assertions
- Tests must be independent and repeatable
- No network dependencies in unit tests
- Mock external services when testing storage/witness services

### Writing Tests for Core

```java
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MyNewFeatureTest {

    @Test
    void shouldRoundtripCorrectly() {
        byte[] data = "test data".getBytes();
        int n = 5, k = 3;

        RainResult result = RainDropsCore.drop(data, n, k, 30);
        List<Drop> drops = result.getDrops().subList(0, k);

        byte[] recovered = RainDropsCore.reconstruct(
            drops, result.getMasterKey(),
            result.getCiphertext(), k, result.isDirectMode()
        );

        assertThat(recovered).isEqualTo(data);
    }
}
```

## Pull Request Process

1. **Fork** the repository
2. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/my-new-feature
   ```
3. **Make your changes** following coding standards
4. **Write tests** for new functionality
5. **Run all tests** to ensure nothing is broken
6. **Commit** with clear messages:
   ```
   feat: add new witness endpoint for batch verification
   fix: correct RainMap combined payload format
   docs: update API reference with storage endpoints
   ```
7. **Push** and open a Pull Request against `main`

### What to include in a PR

- Description of the change
- Related issue number (if applicable)
- Screenshots for UI changes
- Test results
- Updated documentation

## Building Docker Images

### Local Build

```bash
# Storage node
docker build -f Dockerfile.storage -t raindrops/storage:latest .

# Witness node
docker build -f Dockerfile.witness -t raindrops/witness:latest .
```

### Multi-arch Build

```bash
# Set up buildx
docker buildx create --use

# Storage node (ARM64 + AMD64)
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f Dockerfile.storage \
  -t raindrops/storage:latest \
  --push .

# Witness node (ARM64 + AMD64)
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f Dockerfile.witness \
  -t raindrops/witness:latest \
  --push .
```

### Full Stack with Docker Compose

```bash
docker compose -f docker-compose.yml up -d --build
```

---

*Rain Drops — Contributing Guide — Edwar Antonio Ramírez Castillo, 2026*
