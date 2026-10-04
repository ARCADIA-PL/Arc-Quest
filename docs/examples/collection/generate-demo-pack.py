"""Regenerate self-contained JSON demos and original pixel reference textures (Python standard library)."""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent / "collection-demo-pack"
NS = "arc_quest_examples"

def target_pack_version():
    properties = ROOT / "gradle.properties"
    minecraft_version = None
    for line in properties.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.strip().partition("=")
        if separator and key.strip() == "minecraft_version":
            minecraft_version = value.strip()
            break
    pack_formats = {"1.20.1": 15, "1.21.1": 48}
    if minecraft_version not in pack_formats:
        raise ValueError(f"Unsupported or missing minecraft_version in {properties}: {minecraft_version!r}")
    return minecraft_version, pack_formats[minecraft_version]

MINECRAFT_VERSION, PACK_FORMAT = target_pack_version()

# Load the canonical playable design from the Java demo builders.
# demo-blueprints.py rejects unknown DSL features rather than silently dropping rules.
import runpy
documents = runpy.run_path(str(Path(__file__).with_name("demo-blueprints.py")))["build_documents"](ROOT, NS)

def translation_keys(value):
    if isinstance(value, dict):
        if value.get("mode") == "translatable": yield value["value"]
        if value.get("mode") == "literal" and value.get("value"):
            raise ValueError("Demo player text must use a translation key: " + value["value"])
        for child in value.values(): yield from translation_keys(child)
    elif isinstance(value, list):
        for child in value: yield from translation_keys(child)

keys = set(translation_keys(documents))
for locale in ("zh_cn", "en_us"):
    language = json.loads((ROOT / f"src/generated/resources/assets/arc_quest/lang/{locale}.json").read_text(encoding="utf-8"))
    missing = sorted(key for key in keys if not language.get(key))
    if missing: raise ValueError(f"Missing {locale} demo translations: {missing}")

json_dir = OUT / "data" / NS / "arc_quest" / "quests"
json_dir.mkdir(parents=True, exist_ok=True)
for name, document in documents.items(): (json_dir / f"{name}.json").write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(OUT / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": PACK_FORMAT, "description": f"ArcQ Collection Quest examples ({MINECRAFT_VERSION})"}}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def png(path, mineral=False):
    width, height = 240, 120
    pixels = bytearray(bytes((216, 231, 213, 255)) * width * height)
    def rect(x, y, w, h, color):
        for yy in range(max(0, y), min(height, y + h)):
            for xx in range(max(0, x), min(width, x + w)):
                pos = (yy * width + xx) * 4; pixels[pos:pos + 4] = bytes((*color, 255))
    rect(0, 88, 240, 32, (70, 108, 80))
    rect(0, 96, 240, 24, (113, 99, 80))
    for x, y in [(12, 20), (82, 12), (157, 23), (199, 14)]:
        rect(x + 12, y + 30, 8, 52, (115, 86, 63)); rect(x, y, 36, 38, (64, 109, 76)); rect(x + 4, y + 8, 28, 35, (77, 133, 91))
    if mineral:
        rect(32, 36, 172, 66, (148, 152, 143))
        for x, y in [(42, 50), (66, 72), (109, 48), (151, 80), (181, 57)]:
            rect(x, y, 14, 10, (56, 59, 58)); rect(x + 2, y + 2, 5, 3, (73, 76, 73))
        rect(104, 62, 43, 24, (213, 217, 205)); rect(108, 58, 35, 5, (237, 239, 227)); rect(108, 82, 35, 5, (174, 182, 170))
    else:
        for x, shade in [(46, (74, 132, 62)), (132, (198, 200, 183))]:
            rect(x, 60, 20, 20, shade); rect(x + 3, 65, 5, 4, (37, 47, 38)); rect(x + 13, 65, 4, 4, (37, 47, 38)); rect(x + 7, 74, 7, 3, (56, 68, 48))
            rect(x + 2, 80, 16, 18, (73, 98, 107)); rect(x + 2, 98, 6, 13, (69, 77, 108)); rect(x + 12, 98, 6, 13, (69, 77, 108))
    raw = b"".join(b"\x00" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
    def chunk(kind, data): return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)

media_dir = ROOT / "src/main/resources/assets/arc_quest/textures/gui/collection"
png(media_dir / "field_notes.png"); png(media_dir / "mineral_notes.png", mineral=True)
print(f"Wrote 3 self-contained JSON quests to {OUT} (Minecraft {MINECRAFT_VERSION}, pack_format={PACK_FORMAT}) and 2 original reference images to {media_dir}")
