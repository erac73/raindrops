"""
Rain Drops — Distributed Cryptographic Storage
Core Implementation in Python

Modelo: datos fragmentados en micro-unidades sin significado individual,
reconstruibles solo bajo condiciones controladas.
"""

import os
import json
import hashlib
import secrets
from typing import List, Tuple, Dict, Optional
from dataclasses import dataclass, field
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


# ─────────────────────────────────────────────
# SHAMIR'S SECRET SHARING — Aritmética en GF(257)
# Usamos primo 257 para trabajar con bytes (0-255) + seguridad
# ─────────────────────────────────────────────

PRIME = 257  # Primer primo > 255, permite trabajar con bytes


def _eval_polynomial(coefficients: List[int], x: int) -> int:
    """Evalúa un polinomio en x usando el esquema de Horner (mod PRIME)."""
    result = 0
    for coeff in reversed(coefficients):
        result = (result * x + coeff) % PRIME
    return result


def _lagrange_interpolation(points: List[Tuple[int, int]], x: int = 0) -> int:
    """Interpolación de Lagrange para recuperar f(x) dado un conjunto de puntos."""
    k = len(points)
    result = 0
    for i in range(k):
        xi, yi = points[i]
        numerator = yi
        denominator = 1
        for j in range(k):
            if i != j:
                xj, _ = points[j]
                numerator = (numerator * (x - xj)) % PRIME
                denominator = (denominator * (xi - xj)) % PRIME
        # Inverso modular (Fermat: a^(p-2) mod p)
        inv_denominator = pow(denominator, PRIME - 2, PRIME)
        result = (result + numerator * inv_denominator) % PRIME
    return result


def split_secret(secret_byte: int, n: int, k: int) -> List[Tuple[int, int]]:
    """
    Fragmenta un byte secreto en N drops. Se necesitan K para reconstruir.
    Retorna lista de (x, y) — los drops.
    """
    assert 0 <= secret_byte <= 255
    assert 1 < k <= n

    # Polinomio de grado k-1 con término independiente = secret_byte
    coefficients = [secret_byte] + [secrets.randbelow(PRIME) for _ in range(k - 1)]

    drops = []
    for x in range(1, n + 1):
        y = _eval_polynomial(coefficients, x)
        drops.append((x, y))
    return drops


def reconstruct_secret(drops: List[Tuple[int, int]]) -> int:
    """Reconstruye el byte secreto a partir de K drops."""
    return _lagrange_interpolation(drops, x=0)


# ─────────────────────────────────────────────
# FRAGMENTACIÓN DE DATOS COMPLETOS
# ─────────────────────────────────────────────

@dataclass
class Drop:
    """Una micro-unidad de datos sin significado individual."""
    storm_id: str          # ID anónimo de la "tormenta" (dato original)
    drop_index: int        # Posición (x) del drop
    shares: List[int]      # Valores y para cada byte del secreto
    metadata: Dict = field(default_factory=dict)

    def to_dict(self) -> Dict:
        return {
            "storm_id": self.storm_id,
            "drop_index": self.drop_index,
            "shares": self.shares,
            "metadata": self.metadata
        }

    @classmethod
    def from_dict(cls, data: Dict) -> "Drop":
        return cls(
            storm_id=data["storm_id"],
            drop_index=data["drop_index"],
            shares=data["shares"],
            metadata=data.get("metadata", {})
        )


@dataclass
class Storm:
    """Metadatos de un dato almacenado como Rain Drops."""
    storm_id: str
    n: int              # Total de drops
    k: int              # Umbral de reconstrucción
    data_length: int    # Longitud del dato cifrado
    cipher_nonce: bytes # Nonce del cifrado simétrico
    data_type: str      # Tipo de dato (credential, document, health, media)

    def to_dict(self) -> Dict:
        return {
            "storm_id": self.storm_id,
            "n": self.n,
            "k": self.k,
            "data_length": self.data_length,
            "cipher_nonce": self.cipher_nonce.hex(),
            "data_type": self.data_type
        }


class RainDrops:
    """
    Motor principal de Rain Drops.
    Maneja fragmentación, cifrado y reconstrucción.
    """

    # Parámetros recomendados por tipo de dato
    PROFILES = {
        "credential": {"n": 5, "k": 3},
        "document":   {"n": 10, "k": 6},
        "health":     {"n": 7, "k": 5},
        "media":      {"n": 20, "k": 10},
    }

    def drop(
        self,
        data: bytes,
        data_type: str = "document",
        n: Optional[int] = None,
        k: Optional[int] = None,
    ) -> Tuple[Storm, List[Drop]]:
        """
        Fragmenta y cifra datos en N drops.
        
        Args:
            data: Datos a almacenar
            data_type: Tipo de dato (afecta perfil N/K)
            n: Número total de drops (override)
            k: Umbral de reconstrucción (override)
        
        Returns:
            (Storm, [Drop, ...]) — metadatos + lista de drops
        """
        profile = self.PROFILES.get(data_type, self.PROFILES["document"])
        n = n or profile["n"]
        k = k or profile["k"]

        # 1. Generar clave simétrica aleatoria
        symmetric_key = secrets.token_bytes(32)  # AES-256

        # 2. Cifrar los datos con la clave simétrica
        nonce = secrets.token_bytes(12)  # 96 bits para AES-GCM
        aesgcm = AESGCM(symmetric_key)
        ciphertext = aesgcm.encrypt(nonce, data, None)

        # 3. Generar Storm ID (no revela nada sobre el dato)
        storm_id = hashlib.sha256(secrets.token_bytes(32)).hexdigest()

        # 4. Fragmentar la clave simétrica byte a byte
        drops = []
        for byte_idx, secret_byte in enumerate(symmetric_key):
            byte_shares = split_secret(secret_byte, n, k)
            # byte_shares[i] = (x, y) — x es el índice del drop (1..N)

            # Agrupar por drop_index
            if byte_idx == 0:
                for x, y in byte_shares:
                    drops.append(Drop(
                        storm_id=storm_id,
                        drop_index=x,
                        shares=[y],
                        metadata={"byte_count": len(symmetric_key)}
                    ))
            else:
                for i, (x, y) in enumerate(byte_shares):
                    drops[i].shares.append(y)

        storm = Storm(
            storm_id=storm_id,
            n=n,
            k=k,
            data_length=len(ciphertext),
            cipher_nonce=nonce,
            data_type=data_type
        )

        return storm, drops, ciphertext

    def condense(
        self,
        storm: Storm,
        drops: List[Drop],
        ciphertext: bytes,
    ) -> bytes:
        """
        Reconstruye el dato original a partir de K drops.
        
        Args:
            storm: Metadatos del dato
            drops: Al menos K drops
            ciphertext: Datos cifrados
        
        Returns:
            Dato original descifrado
        
        Raises:
            ValueError: Si hay menos de K drops o son inválidos
        """
        if len(drops) < storm.k:
            raise ValueError(
                f"Se necesitan al menos {storm.k} drops, "
                f"se proporcionaron {len(drops)}"
            )

        # Usar exactamente K drops
        active_drops = drops[:storm.k]
        key_bytes = []

        # Reconstruir la clave simétrica byte a byte
        num_key_bytes = len(active_drops[0].shares)
        for byte_idx in range(num_key_bytes):
            points = [(d.drop_index, d.shares[byte_idx]) for d in active_drops]
            secret_byte = reconstruct_secret(points)
            key_bytes.append(secret_byte)

        symmetric_key = bytes(key_bytes)

        # Descifrar los datos
        aesgcm = AESGCM(symmetric_key)
        plaintext = aesgcm.decrypt(storm.cipher_nonce, ciphertext, None)

        return plaintext

    def verify_drop(self, drop: Drop, storm: Storm) -> bool:
        """Verifica que un drop pertenece a la tormenta indicada."""
        return drop.storm_id == storm.storm_id


# ─────────────────────────────────────────────
# SIMULACIÓN DE NODOS (IN-MEMORY)
# ─────────────────────────────────────────────

class KeeperNode:
    """
    Nodo guardián: almacena drops cifrados.
    No conoce el contenido ni la relación entre drops.
    """

    def __init__(self, node_id: str):
        self.node_id = node_id
        self._storage: Dict[str, Drop] = {}

    def store(self, drop: Drop) -> str:
        """Almacena un drop. Retorna ticket de almacenamiento."""
        ticket = hashlib.sha256(
            f"{drop.storm_id}:{drop.drop_index}:{self.node_id}".encode()
        ).hexdigest()[:16]
        self._storage[ticket] = drop
        return ticket

    def retrieve(self, ticket: str, requester_token: str) -> Optional[Drop]:
        """
        Entrega un drop si el token del solicitante es válido.
        En producción: verificar firma criptográfica del token.
        """
        # Simulación simplificada de verificación
        if requester_token and ticket in self._storage:
            return self._storage[ticket]
        return None

    def status(self) -> Dict:
        return {"node_id": self.node_id, "drops_stored": len(self._storage)}


# ─────────────────────────────────────────────
# DEMO DE USO
# ─────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 60)
    print("Rain Drops — Demo de Fragmentación y Reconstrucción")
    print("=" * 60)

    rd = RainDrops()
    nodes = [KeeperNode(f"keeper_{i}") for i in range(10)]

    # ── Caso 1: Credencial ──────────────────────
    print("\n🔑 CASO 1: Credencial (clave privada)")
    secret_key = b"mi_clave_privada_super_secreta_32b!"
    storm, drops, ciphertext = rd.drop(secret_key, data_type="credential")

    print(f"  Storm ID:   {storm.storm_id[:24]}...")
    print(f"  Drops generados: {storm.n} (umbral: {storm.k})")
    print(f"  Tamaño ciphertext: {len(ciphertext)} bytes")
    print(f"  Cada drop: {len(drops[0].shares)} valores (uno por byte de clave)")

    # Distribuir drops en nodos
    tickets = []
    for i, drop in enumerate(drops):
        ticket = nodes[i % len(nodes)].store(drop)
        tickets.append((nodes[i % len(nodes)], ticket))

    # Reconstruir con K drops (simulamos recolección)
    print(f"\n  Recolectando {storm.k} drops de {storm.n} nodos...")
    collected = []
    for node, ticket in tickets[:storm.k]:
        d = node.retrieve(ticket, requester_token="usuario_autenticado")
        if d:
            collected.append(d)

    recovered = rd.condense(storm, collected, ciphertext)
    assert recovered == secret_key
    print(f"  ✅ Reconstrucción exitosa: '{recovered.decode()}'")

    # ── Caso 2: Fallo con K-1 drops ─────────────
    print("\n🚫 CASO 2: Intento con K-1 drops (debe fallar)")
    try:
        insufficient_drops = collected[:storm.k - 1]
        rd.condense(storm, insufficient_drops, ciphertext)
    except ValueError as e:
        print(f"  ✅ Error esperado: {e}")

    # ── Caso 3: Dato de salud ────────────────────
    print("\n🏥 CASO 3: Historial médico (perfil 'health')")
    health_record = json.dumps({
        "paciente": "Ana García",
        "diagnostico": "Hipertensión leve",
        "medicamentos": ["Losartán 50mg"],
        "alergias": ["Penicilina"]
    }).encode()

    storm2, drops2, ct2 = rd.drop(health_record, data_type="health")
    print(f"  Storm ID:   {storm2.storm_id[:24]}...")
    print(f"  Perfil:     N={storm2.n}, K={storm2.k}")

    recovered2 = rd.condense(storm2, drops2[:storm2.k], ct2)
    record = json.loads(recovered2)
    print(f"  ✅ Paciente recuperado: {record['paciente']}")
    print(f"     Diagnóstico: {record['diagnostico']}")

    print("\n" + "=" * 60)
    print("✅ Todos los casos completados correctamente")
    print("=" * 60)
