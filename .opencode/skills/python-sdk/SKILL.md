---
name: python-sdk
description: Python SDK development - packaging, testing, documentation, type hints
license: MIT
compatibility: opencode
metadata:
  language: python
  packaging: pip
---

## What I do

Guide Python SDK development and packaging.

## Project Structure

```
my-sdk/
├── src/
│   └── my_sdk/
│       ├── __init__.py
│       ├── client.py
│       ├── models.py
│       └── utils.py
├── tests/
│   ├── __init__.py
│   ├── test_client.py
│   └── test_models.py
├── pyproject.toml
├── README.md
└── LICENSE
```

## Type Hints

```python
from typing import Optional, List, Dict, Union
from dataclasses import dataclass

@dataclass
class Drop:
    id: str
    x: int
    y: int
    mac: str
    ttl: int = 0

def store_drop(
    client: 'RainClient',
    drop: Drop,
    timeout: float = 30.0,
    retries: int = 3
) -> bool:
    ...
```

## Packaging (pyproject.toml)

```toml
[build-system]
requires = ["setuptools>=61.0"]
build-backend = "setuptools.backends._legacy:_Backend"

[project]
name = "raindrops-sdk"
version = "1.0.0"
description = "Python SDK for Rain Drops"
readme = "README.md"
license = {text = "MIT"}
requires-python = ">=3.9"
dependencies = [
    "cryptography>=41.0",
    "requests>=2.28",
]

[project.optional-dependencies]
dev = [
    "pytest>=7.0",
    "pytest-cov>=4.0",
    "black>=23.0",
    "mypy>=1.0",
]
```

## Testing

```python
# test_client.py
import pytest
from raindrops_sdk import RainClient

class TestRainClient:
    @pytest.fixture
    def client(self):
        return RainClient("http://localhost:9080")
    
    def test_create_client(self, client):
        assert client is not None
    
    def test_store_drop(self, client):
        result = client.store_drop("test-payload")
        assert result is not None
```

### Running Tests
```bash
pytest tests/
pytest --cov=raindrops_sdk tests/
pytest -v -x tests/
```

## Code Quality

### Black (Formatter)
```bash
black src/ tests/
black --check src/ tests/
```

### MyPy (Type Checker)
```bash
mypy src/
mypy --strict src/
```

### Ruff (Linter)
```bash
ruff check src/ tests/
ruff format src/ tests/
```

## Documentation

```python
def store_drop(
    payload: bytes,
    n: int = 3,
    k: int = 2,
    *,
    ttl: int = 86400,
    api_key: Optional[str] = None
) -> RainMap:
    """Store a payload across multiple storage nodes.
    
    Args:
        payload: Data to store (max 65KB)
        n: Number of storage nodes
        k: Minimum nodes to reconstruct
        ttl: Time-to-live in seconds
        api_key: Optional API key
        
    Returns:
        RainMap containing drop locations
        
    Raises:
        StorageError: If storage fails
        ValidationError: If parameters invalid
    """
    ...
```

## When to use me

Use this skill for Python SDK development, packaging, testing, or documentation.
