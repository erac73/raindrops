---
name: java-springboot
description: Java Spring Boot best practices for JPA, security, testing, and production deployment
license: MIT
compatibility: opencode
metadata:
  audience: java-developers
  framework: spring-boot-3.x
---

## What I do

Guide Java Spring Boot development following industry best practices.

## Project Structure Pattern

```
src/main/java/com/example/
├── config/           # Security, CORS, rate limiting, interceptors
├── controller/       # REST controllers (thin layer)
├── model/            # JPA entities (POJOs or records)
├── repository/       # Spring Data JPA interfaces
├── service/          # Business logic (annotated @Service)
└── exception/        # Custom exceptions + @ControllerAdvice
```

## Spring Boot Best Practices

### Controllers
- Thin layer: only HTTP concerns (request/response mapping)
- Delegate all business logic to services
- Use `@RestController` with `@RequestMapping`
- Return `ResponseEntity<T>` for status control
- Validate input with `@Valid` + Jakarta Bean Validation

### Services
- Annotate with `@Service`
- Use `@Transactional` on write methods
- Use `@Transactional(readOnly = true)` on reads
- Throw custom exceptions, let `@ControllerAdvice` handle them
- Inject dependencies via constructor (not @Autowired)

### Repositories
- Extend `JpaRepository<Entity, ID>`
- Use derived query methods: `findByName(String name)`
- Use `@Query` for complex queries
- Avoid `@Modifying` unless necessary

### Security
- API Key filter: use `MessageDigest.isEqual()` (timing-safe)
- Stateless sessions: `SessionCreationPolicy.STATELESS`
- CSRF disabled for REST APIs
- CORS configured explicitly

### Rate Limiting
- Token bucket algorithm per IP+method
- Use `ConcurrentHashMap` with bounded cleanup
- Return 429 with `Retry-After` header
- Consider Caffeine cache for production

### Health Indicators
- Implement `HealthIndicator` for custom checks
- Include: memory, disk, uptime, PID
- Warn at >85% memory or >90% disk
- Use `Health.up()`, `Health.down()`, `Health.status("WARNING")`

### Audit Logging
- Use `HandlerInterceptor` (not Filter)
- Log: requestId (UUID), client IP, method, URI, status, duration
- Use SLF4J named logger: `LoggerFactory.getLogger("AUDIT")`
- Extract real IP from `X-Forwarded-For` header

## Testing

```java
// Unit test
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock private Repository repo;
    @InjectMocks private Service service;
    
    @Test
    void shouldReturnResult() {
        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        Result result = service.get(1L);
        assertNotNull(result);
    }
}

// Integration test
@SpringBootTest
@AutoConfigureMockMvc
class ControllerTest {
    @Autowired MockMvc mvc;
    
    @Test
    void shouldReturn200() throws Exception {
        mvc.perform(get("/endpoint"))
           .andExpect(status().isOk());
    }
}
```

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| N+1 queries | Use `@EntityGraph` or JOIN FETCH |
| LazyInitializationException | Use `@Transactional(readOnly=true)` on service |
| JSON serialization循环 | Use `@JsonIgnore` or `@JsonManagedReference` |
| H2 file lock | Use `DB_CLOSE_DELAY=-1` in JDBC URL |
| Thread pool exhaustion | Use bounded pools, not `newCachedThreadPool()` |
| Memory leak | Clean up ConcurrentHashMap entries periodically |

## When to use me

Use this skill when writing or reviewing Java/Spring Boot code in the project. Follow these patterns for consistency and production readiness.
