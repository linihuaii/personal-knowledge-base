"""S02 offline prototype: parse, chunk, embed, retrieve, persist and probe adapters."""
from __future__ import annotations
import hashlib, json, math, socket
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

DIMENSION = 32
ROOT = Path(__file__).parent

def parse_sample(path: Path) -> str:
    if path.suffix.lower() not in {".txt", ".md", ".markdown"}:
        raise ValueError("unsupported sample type")
    return path.read_text(encoding="utf-8").strip()

def chunk(text: str, size: int = 80) -> list[str]:
    return [text[i:i + size] for i in range(0, len(text), size) if text[i:i + size].strip()]

def embed(text: str, dimension: int = DIMENSION) -> list[float]:
    raw = hashlib.sha256(text.encode("utf-8")).digest()
    values = [((raw[i % len(raw)] / 255.0) * 2.0) - 1.0 for i in range(dimension)]
    norm = math.sqrt(sum(x * x for x in values)) or 1.0
    return [round(x / norm, 8) for x in values]

def cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))

class LocalIndex:
    def __init__(self, path: Path, dimension: int = DIMENSION):
        self.path, self.dimension, self.entries = path, dimension, []
    def add(self, chunk_id: str, kb: str, doc: str, text: str) -> None:
        self.entries.append({"chunk_id": chunk_id, "knowledge_base_id": kb, "document_id": doc, "text": text, "vector": embed(text, self.dimension)})
    def save(self) -> None:
        temp = self.path.with_suffix(".tmp")
        temp.write_text(json.dumps({"dimension": self.dimension, "entries": self.entries}, ensure_ascii=False), encoding="utf-8")
        temp.replace(self.path)
    @classmethod
    def load(cls, path: Path) -> "LocalIndex":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload["dimension"] != DIMENSION:
            raise ValueError("embedding dimension mismatch")
        index = cls(path, payload["dimension"]); index.entries = payload["entries"]; return index
    def delete_document(self, document_id: str) -> None:
        self.entries = [e for e in self.entries if e["document_id"] != document_id]
    def search(self, kb: str, query: str) -> list[dict]:
        q = embed(query, self.dimension)
        rows = [e for e in self.entries if e["knowledge_base_id"] == kb]
        rows.sort(key=lambda e: cosine(q, e["vector"]), reverse=True)
        return [{"chunk_id": e["chunk_id"], "document_id": e["document_id"]} for e in rows]

def probe(url: str) -> str:
    try:
        with urlopen(Request(url, method="GET"), timeout=1) as response:
            return f"reachable:{response.status}"
    except (URLError, TimeoutError, socket.timeout, ConnectionError) as exc:
        return f"not_verified:{type(exc).__name__}"

def main() -> None:
    samples, work = ROOT / ".prototype-samples", ROOT / ".prototype-tmp"
    samples.mkdir(exist_ok=True); work.mkdir(exist_ok=True)
    try:
        (samples / "notes.txt").write_text("Java 集合框架。List 保存有序元素。", encoding="utf-8")
        (samples / "notes.md").write_text("# Python\nPython 列表保存有序元素。", encoding="utf-8")
        parsed = [parse_sample(samples / name) for name in ("notes.txt", "notes.md")]
        chunks = [part for text in parsed for part in chunk(text, 20)]
        path, index = work / "manifest.json", LocalIndex(work / "manifest.json")
        for number, text in enumerate(chunks):
            index.add(f"c{number}", "kb-java" if number == 0 else "kb-python", f"doc-{number}", text)
        index.save(); same_kb = index.search("kb-java", "Java 集合"); other_kb = index.search("kb-python", "Java 集合")
        reloaded = LocalIndex.load(path); reloaded.delete_document("doc-0"); reloaded.save(); after_delete = reloaded.search("kb-java", "Java 集合")
        bad = json.loads(path.read_text(encoding="utf-8")); bad["dimension"] = DIMENSION + 1; path.write_text(json.dumps(bad), encoding="utf-8")
        try: LocalIndex.load(path); dimension_check = "failed"
        except ValueError: dimension_check = "passed"
        assert parsed and chunks and same_kb and all(item["document_id"] != "doc-0" for item in other_kb) and not after_delete and dimension_check == "passed"
        print(json.dumps({"parsing": "passed", "chunking": len(chunks), "same_kb_hits": same_kb, "cross_kb_filter": "passed", "restart": "passed", "delete_consistency": "passed", "dimension_mismatch": dimension_check, "openai_compatible_probe": probe("http://127.0.0.1:8000/v1/models"), "ollama_probe": probe("http://127.0.0.1:11434/api/tags")}, ensure_ascii=False, indent=2))
    finally:
        for directory in (work, samples):
            for child in directory.iterdir(): child.unlink()
            directory.rmdir()

if __name__ == "__main__": main()
