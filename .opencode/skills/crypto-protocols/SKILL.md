---
name: crypto-protocols
description: Cryptographic protocols - SSS, VSS, threshold signatures, MPC, secure channels
license: MIT
compatibility: opencode
metadata:
  domain: cryptography
  paradigm: distributed
---

## What I do

Guide cryptographic protocol design and implementation.

## Core Protocols

### Shamir's Secret Sharing (SSS)
- Splits secret into n shares, any k can reconstruct
- Used in Rain Drops for key distribution
- GF(p) arithmetic for finite field operations

### Feldman's VSS (Verifiable Secret Sharing)
- Adds commitment values to SSS
- Verifiers can check shares are consistent
- Prevents cheating dealer

### Proactive Secret Sharing
- Periodically refresh shares without changing secret
- Compromised shares become useless after refresh
- Protects against mobile adversaries

### Threshold Signatures (TSS)
- Distributed signing without reconstructing key
- t-of-n signers produce valid signature
- Prevents single point of failure

## Mathematical Foundations

### Finite Fields
```
GF(p) where p = 2^521 - 1 (Mersenne prime)
Operations: add, sub, mul, div, pow, inverse
```

### Polynomial Operations
```
Evaluation: f(x) = a_0 + a_1*x + a_2*x^2 + ... + a_{k-1}*x^{k-1}
Lagrange interpolation for reconstruction
```

## When to use me

Use this skill for cryptographic protocol design, SSS/VSS implementation, or threshold cryptography.
