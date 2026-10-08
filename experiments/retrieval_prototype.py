"""Offline S02 prototype: deterministic local vector index lifecycle."""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path


DIMENSION = 32


def embed(text: str) -> list[float]:
    raw = hashlib.sha256(text.encode("utf-8")).digest()
    values = [((raw[i % len(raw)] / 255.0) * 2.0) - 1.0 for i in range(DIMENSION)]
    norm = math.sqrt(sum(x * x for x in values)) or 1.0
    return [round(x / norm, 8) for x in values]


def cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


class LocalIndex:
    def __init__(self, path: Path, dimension: int = DIMENSION):
        self.path = path
        self.dimension = dimension
        self.entries: list[dict] = []

    def add(self, chunk_id: str, knowledge_base_id: str, document_id: str, text: str) -> None:
        self.entries.append({
            "chunk_id": chunk_id,
            "knowledge_base_id": knowledge_base_id,
            "document_id": document_id,
            "text": text,
            "vector": embed(text),
        })

    def save(self) -> None:
        payload = {"dimension": self.dimension, "entries": self.entries}
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        temporary.replace(self.path)

    @classmethod
    def load(cls, path: Path) -> "LocalIndex":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload["dimension"] != DIMENSION:
            raise ValueError("embedding dimension mismatch")
        index = cls(path, payload["dimension"])
        index.entries = payload["entries"]
        return index

    def delete_document(self, document_id: str) -> None:
        self.entries = [entry for entry in self.entries if entry["document_id"] != document_id]

    def search(self, knowledge_base_id: str, query: str, limit: int = 3) -> list[dict]:
        query_vector = embed(query)
        candidates = [e for e in self.entries if e["knowledge_base_id"] == knowledge_base_id]
        ranked = sorted(candidates, key=lambda e: cosine(query_vector, e["vector"]), reverse=True)
        return [{"chunk_id": e["chunk_id"], "document_id": e["document_id"]} for e in ranked[:limit]]


def main() -> None:
    directory = Path(__file__).with_name(".prototype-tmp")
    directory.mkdir(exist_ok=True)
    try:
        path = directory / "manifest.json"
        index = LocalIndex(path)
        index.add("c1", "kb-java", "doc-a", "Java 集合框架和 List")
        index.add("c2", "kb-java", "doc-b", "Java Stream API")
        index.add("c3", "kb-python", "doc-c", "Python 列表")
        index.save()

        same_kb = index.search("kb-java", "Java 集合")
        cross_kb = index.search("kb-python", "Java 集合")
        reloaded = LocalIndex.load(path)
        reloaded.delete_document("doc-a")
        reloaded.save()
        after_delete = reloaded.search("kb-java", "Java 集合")

        assert same_kb and all(item["document_id"] in {"doc-a", "doc-b"} for item in same_kb)
        assert all(item["document_id"] == "doc-c" for item in cross_kb)
        assert all(item["document_id"] != "doc-a" for item in after_delete)
        print(json.dumps({
                "dimension": DIMENSION,
                "same_kb_hits": same_kb,
                "filtered_hits": cross_kb,
                "after_delete_hits": after_delete,
                "restart": "passed",
                "delete_consistency": "passed",
            }, ensure_ascii=False, indent=2))
    finally:
        for child in directory.iterdir():
            child.unlink()
        directory.rmdir()


if __name__ == "__main__":
    main()
