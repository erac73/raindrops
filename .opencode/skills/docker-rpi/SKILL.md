---
name: docker-rpi
description: Docker management on Raspberry Pi - compose, troubleshooting, builds, monitoring
license: MIT
compatibility: opencode
metadata:
  platform: raspberry-pi
  environment: production
---

## What I do

Guide Docker operations on Raspberry Pi 5 for the Rain Drops project.

## Current Setup

```yaml
# docker-compose.yml location
/home/serpico/raindrops-fase1/docker-compose.yml

# Containers
raindrops-witness     → :9080 (orchestrator)
raindrops-storage-1   → :9081 (keeper-1)
raindrops-storage-2   → :9082 (keeper-2)
raindrops-storage-3   → :9083 (keeper-3)
raindrops-docs        → :8089 (nginx static)
```

## Essential Commands

```bash
# Status
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep raindrops

# Logs
docker logs raindrops-witness --tail 20
docker logs raindrops-storage-1 -f  # follow

# Rebuild single service
docker compose build --no-cache witness-node
docker compose up -d --force-recreate witness-node

# Rebuild all
docker compose build --no-cache
docker compose up -d --force-recreate

# Enter container
docker exec -it raindrops-witness bash

# Check health
curl -s http://172.17.0.1:9080/health | python3 -m json.tool

# Clean up
docker system prune -f
docker volume prune -f
```

## Build Patterns

### Multi-stage Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine AS runtime
# Copy pre-built JAR
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Build Strategy
1. `mvn compile` locally (on Pi)
2. `docker compose build --no-cache` (uses COPY for JAR)
3. `docker compose up -d --force-recreate`

### Why Not Maven in Docker?
- Pi has limited resources
- Maven build takes 2-3 minutes
- Pre-building avoids container startup delays

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Container won't start | `docker logs <container>` — check Spring Boot errors |
| Health check failing | Verify `/health` endpoint manually |
| Port conflict | `netstat -tlnp | grep :9080` — kill old process |
| Volume permission | `docker compose down -v` then recreate |
| OOM killed | Check `docker stats` — increase memory limit |
| Network unreachable | `docker network inspect raindrops-fase1_rain-net` |
| Build cache issues | `docker compose build --no-cache` |
| Stale images | `docker image prune -f` |

## Network Architecture

```
Host (Pi5)                    Docker Network (rain-net)
┌─────────────┐              ┌─────────────────────────┐
│ :9080 ──────┼─────────────→│ witness:8080             │
│ :9081 ──────┼─────────────→│ keeper-1:8080            │
│ :9082 ──────┼─────────────→│ keeper-2:8080            │
│ :9083 ──────┼─────────────→│ keeper-3:8080            │
│ :8089 ──────┼─────────────→│ docs:80 (nginx)          │
└─────────────┘              └─────────────────────────┘
```

## Volume Management

```bash
# List volumes
docker volume ls | grep raindrops

# Inspect volume
docker volume inspect raindrops-fase1_storage-1-data

# Backup H2 database
docker exec raindrops-storage-1 cat /app/data/storage-db.mv.db > backup.db

# Restore H2 database
cat backup.db | docker exec -i raindrops-storage-1 tee /app/data/storage-db.mv.db > /dev/null
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `STORAGE_URLS` | (empty) | Comma-separated storage node URLs |
| `PEER_URLS` | (empty) | Comma-separated peer URLs for replication |
| `NODE_ID` | `storage-node` | Unique node identifier |
| `API_KEY` | (empty) | API key for authentication |
| `WITNESS_PORT` | `8080` | Witness internal port |
| `STORAGE_PORT` | `8080` | Storage internal port |

## When to use me

Use this skill when managing Docker containers on the Pi — rebuilding, troubleshooting, monitoring, or reconfiguring services.
