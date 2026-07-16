---
name: api-design
description: REST API design - OpenAPI, versioning, error handling, pagination, HATEOAS
license: MIT
compatibility: opencode
metadata:
  style: rest
  spec: openapi-3.0
---

## What I do

Guide REST API design with OpenAPI specifications.

## RESTful Principles

### Resources
- Nouns, not verbs: `/drops` not `/getDrops`
- Plural: `/drops` not `/drop`
- Hierarchical: `/rainmaps/{id}/drops`

### HTTP Methods
- GET: Retrieve (idempotent)
- POST: Create
- PUT: Replace (idempotent)
- PATCH: Partial update
- DELETE: Remove (idempotent)

### Status Codes
- 200: OK
- 201: Created
- 204: No Content
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 409: Conflict
- 429: Too Many Requests
- 500: Internal Server Error

## OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: Rain Drops API
  version: 1.0.0
paths:
  /drops:
    post:
      summary: Store a drop
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Drop'
      responses:
        '200':
          description: Drop stored
```

## Error Handling

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid drop ID format",
    "details": [
      {
        "field": "dropId",
        "issue": "Must be UUID format"
      }
    ]
  }
}
```

## Pagination

```
GET /drops?page=0&size=20&sort=createdAt,desc

Response:
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

## Rate Limiting

```
X-Rate-Limit-Limit: 100
X-Rate-Limit-Remaining: 95
X-Rate-Limit-Reset: 1623456789
```

## Versioning

### URL Versioning
```
/api/v1/drops
/api/v2/drops
```

### Header Versioning
```
Accept: application/vnd.raindrops.v1+json
```

## When to use me

Use this skill when designing REST APIs, writing OpenAPI specs, or implementing error handling.
