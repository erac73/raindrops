---
name: git-workflow
description: Git workflows - branching, commit conventions, code review, CI/CD
license: MIT
compatibility: opencode
metadata:
  vcs: git
  conventions: conventional-commits
---

## What I do

Guide Git workflows, branching strategies, and commit conventions.

## Branching Strategies

### GitHub Flow (Simple)
```
main (production)
  └── feature/my-feature
  └── bugfix/my-bugfix
```

### Git Flow (Complex)
```
main (production)
  └── develop
      └── feature/my-feature
      └── release/v1.0
      └── hotfix/my-fix
```

## Commit Conventions

### Conventional Commits
```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code restructuring
- `test`: Adding tests
- `chore`: Maintenance

### Examples
```
feat(storage): add drop expiration

Add TTL-based expiration for drops.
Drops are now automatically removed after their TTL expires.

Closes #42
```

```
fix(witness): resolve race condition in replication

Fix concurrent modification of dropIds list during replication.
Use ConcurrentHashMap for thread-safe operations.

Fixes #38
```

## Pull Request Workflow

### 1. Create Feature Branch
```bash
git checkout -b feat/my-feature develop
```

### 2. Make Changes & Commit
```bash
git add -A
git commit -m "feat(module): add feature"
```

### 3. Push & Create PR
```bash
git push origin feat/my-feature
# Create PR on GitHub/Gitea
```

### 4. Code Review
- Review for correctness
- Check test coverage
- Verify documentation

### 5. Merge & Cleanup
```bash
git checkout develop
git merge --no-ff feat/my-feature
git branch -d feat/my-feature
git push origin --delete feat/my-feature
```

## Git Hooks

### Pre-commit (.git/hooks/pre-commit)
```bash
#!/bin/bash
mvn compile -q -DskipTests
if [ $? -ne 0 ]; then
  echo "Compilation failed. Commit aborted."
  exit 1
fi
```

### Commit Message (.git/hooks/commit-msg)
```bash
#!/bin/bash
# Validate conventional commit format
pattern="^(feat|fix|docs|style|refactor|test|chore)(\([a-z-]+\))?: .{1,72}"
if ! grep -qE "$pattern" "$1"; then
  echo "Invalid commit message format"
  exit 1
fi
```

## When to use me

Use this skill for Git workflows, commit conventions, code review processes, or CI/CD setup.
