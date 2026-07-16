---
name: security-audit
description: Security auditing - vulnerability scanning, dependency checks, code review, penetration testing
license: MIT
compatibility: opencode
metadata:
  framework: owasp
  scope: full-stack
---

## What I do

Guide security auditing and vulnerability assessment.

## Audit Checklist

### Authentication
- [ ] Passwords hashed with bcrypt/scrypt
- [ ] MFA available
- [ ] Account lockout after failures
- [ ] Session timeout configured
- [ ] No default credentials

### Authorization
- [ ] RBAC implemented
- [ ] Least privilege principle
- [ ] No horizontal/vertical privilege escalation
- [ ] API endpoints protected

### Cryptography
- [ ] AES-256-GCM for encryption
- [ ] SHA-256+ for hashing
- [ ] RSA-2048+ for signatures
- [ ] No hardcoded keys
- [ ] Secure key storage

### Input Validation
- [ ] SQL injection prevention
- [ ] XSS prevention
- [ ] CSRF protection
- [ ] File upload validation
- [ ] Parameterized queries

### Configuration
- [ ] Debug mode disabled
- [ ] Error messages don't leak info
- [ ] Unnecessary features disabled
- [ ] CORS configured properly

## Vulnerability Scanning

### Dependencies
```bash
# Maven
mvn dependency-check:check

# Python
pip-audit

# JavaScript
npm audit
```

### Static Analysis
```bash
# Java (SpotBugs)
mvn spotbugs:check

# Python (Bandit)
bandit -r src/

# General (Semgrep)
semgrep --config=auto .
```

### Container Scanning
```bash
# Trivy
trivy image myapp:latest

# Docker Scout
docker scout cves myapp:latest
```

## Penetration Testing

### Network
```bash
# Port scanning
nmap -sV -sC target

# Service enumeration
nmap -sV -p 80,443,8080 target
```

### Web
```bash
# Directory brute force
gobuster dir -u http://target -w wordlist.txt

# SQL injection
sqlmap -u "http://target/?id=1" --dbs
```

### API
```bash
# Fuzzing
ffuf -u http://target/api/FUZZ -w wordlist.txt

# JWT testing
jwt_tool <token>
```

## Incident Response

### 1. Detection
- Monitor logs for anomalies
- Set up alerts for suspicious activity

### 2. Containment
- Isolate affected systems
- Preserve evidence

### 3. Eradication
- Remove malware
- Patch vulnerabilities

### 4. Recovery
- Restore from backups
- Verify system integrity

### 5. Lessons Learned
- Document incident
- Update security measures

## When to use me

Use this skill for security audits, vulnerability assessments, penetration testing, or incident response.
