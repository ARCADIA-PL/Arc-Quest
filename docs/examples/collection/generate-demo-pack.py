"""Regenerate self-contained JSON demos and original pixel reference textures (Python standard library)."""
from pathlib import Path
import copy
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

def text(value): return {"mode": "literal", "value": value}
def eid(name): return f"{NS}:codex/{name}"
def objective(key, kind, target, count=1, label=None, tag=None):
    result = {"id": key, "type": f"arc_quest:{kind}", "targetId": target, "requiredCount": count,
              "displayText": text(label or key)}
    if tag: result["itemTag"] = tag
    return result
def binding(key, name, objectives=(), record=None, policy="EXISTING_RECORDS"):
    result = {"bindingId": key, "entryId": eid(name), "objectiveIds": list(objectives),
              "requirementMode": "ALL", "recordPolicy": policy}
    if record: result["recordRequirements"] = record
    return result
def discovered(): return [{"type": "DISCOVERED"}]
def sheet(bindings, quota=0):
    return {"completionPolicy": "QUOTA" if quota else "ALL", "requiredCount": quota,
            "countDistinctEntries": False, "bindings": bindings}
def phase(key, title, objectives, bindings, quota=0, manual=False, targets=()):
    result = {"phaseId": key, "displayName": text(title), "objectives": objectives,
              "collectionSheet": sheet(bindings, quota), "autoAdvanceOnComplete": not manual}
    if targets: result["transitions"] = [{"targetPhaseIds": list(targets)}]
    return result

entries = []
for name, title, kind, target, research in [
    ("zombie", "僵尸", "ENTITY", "zombie", 5), ("skeleton", "骷髅", "ENTITY", "skeleton", 3),
    ("spider", "蜘蛛", "ENTITY", "spider", 3), ("cow", "牛", "ENTITY", "cow", 0),
    ("iron_ingot", "铁锭", "ITEM", "iron_ingot", 2), ("coal", "煤炭", "ITEM", "coal", 5),
    ("logs", "原木", "ITEM", "logs", 8), ("bone", "骨头", "ITEM", "bone", 0),
    ("rotten_flesh", "腐肉", "ITEM", "rotten_flesh", 0),
]:
    action = "interact" if name == "cow" else "kill" if kind == "ENTITY" else "collect"
    entry = {"entryId": eid(name), "categoryId": "living" if kind == "ENTITY" else "materials",
             "displayName": text(title), "description": text(f"{title}调查资料：永久图鉴与本轮任务要求分别保存。"),
             "subjectKind": kind, "discoveryObjectives": [objective("first_record", action, f"minecraft:{target}", 1, f"发现{title}", "minecraft:logs" if name == "logs" else None)],
             "content": [{"blockId": "notes", "text": text(f"观察{title}并记录样本。点击物品图标可以查询 JEI。"), "reveal": "DISCOVERED"}],
             "visibilityMode": "VISIBLE_BY_DEFAULT", "hiddenPresentationMode": "FULLY_HIDDEN", "sortOrder": len(entries)}
    if name == "logs": entry["itemTag"] = "minecraft:logs"
    else: entry["subjectId"] = f"minecraft:{target}"
    if research:
        entry["researchObjectives"] = [objective("study", "craft" if name == "iron_ingot" else action,
                                                 f"minecraft:{target}", research, f"研究{title}", "minecraft:logs" if name == "logs" else None)]
    if name == "spider": entry.update(visibilityMode="HIDDEN_BY_DEFAULT", hiddenPresentationMode="PLACEHOLDER")
    if name in ("zombie", "iron_ingot"):
        entry["content"].append({"blockId": "field_image", "media": {"type": "image", "texture": f"arc_quest:textures/gui/collection/{'field' if name == 'zombie' else 'mineral'}_notes.png", "width": 240, "height": 120},
                                 "caption": text("调查配图，点击可放大"), "fit": "CONTAIN", "zoomable": True, "reveal": "DISCOVERED"})
    if name == "zombie":
        entry["rewards"] = [
            {"rewardId": "zombie_first_record", "trigger": "DISCOVERED", "grantMode": "MANUAL",
             "rewards": [{"type": "item", "itemId": "minecraft:coal", "count": 1}]},
            {"rewardId": "zombie_anatomy", "trigger": "RESEARCH_COMPLETE", "grantMode": "MANUAL",
             "rewards": [{"type": "item", "itemId": "minecraft:iron_nugget", "count": 3}]},
            {"rewardId": "zombie_investigation", "trigger": "BINDING_COMPLETE", "grantMode": "MANUAL",
             "rewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 1}]},
        ]
        entry["relatedItems"] = ["minecraft:rotten_flesh"]
        entry["content"].append({"blockId": "anatomy", "text": text("研究完成后解锁的资料。"), "reveal": "RESEARCH_STEP", "revealStepId": "study"})
    if name == "logs":
        entry["rewards"] = [{"rewardId": "logs_investigation", "trigger": "BINDING_COMPLETE", "grantMode": "AUTO",
                             "rewards": [{"type": "item", "itemId": "minecraft:stick", "count": 2}]}]
    entries.append(entry)

def quest(key, title, phases, repeat=False):
    return {"id": f"{NS}:{key}", "category": "arc_quest:collection", "mode": "COLLECTION", "displayName": text(title),
            "visualConfig": {"themeColor": 0x85C6AE},
            "description": text("JSON 图鉴范例；与内置 Java Demo 使用独立命名空间，可同时加载。"), "repeatable": repeat,
            "initialPhaseId": phases[0]["phaseId"], "completionPolicy": "ALL",
            "collectionConfig": {"categories": [{"categoryId": "living", "displayName": text("生物")}, {"categoryId": "materials", "displayName": text("材料")}], "entries": copy.deepcopy(entries)},
            "phases": phases, "completionRewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 1}]}

field_actions = [objective("zombie_defeats", "kill", "minecraft:zombie", 3, "击败僵尸"),
                 objective("zombie_samples", "offer", "minecraft:rotten_flesh", 2, "提交腐肉样本"),
                 objective("skeleton_defeats", "kill", "minecraft:skeleton", 2, "击败骷髅"),
                 objective("iron_crafting", "craft", "minecraft:iron_ingot", 1, "用铁粒合成铁锭"),
                 objective("logs_action", "collect", "minecraft:logs", 8, "获得原木", "minecraft:logs")]
field_bindings = [binding("zombie", "zombie", ("zombie_defeats", "zombie_samples"), discovered()),
                  binding("skeleton", "skeleton", ("skeleton_defeats",), discovered()), binding("spider", "spider", record=discovered()),
                  binding("cow", "cow", record=discovered()), binding("iron", "iron_ingot", ("iron_crafting",), discovered()),
                  binding("coal", "coal", record=[{"type": "RESEARCH_STEP", "stepId": "study"}]),
                  binding("logs", "logs", ("logs_action",)), binding("bone", "bone", record=discovered())]

run_actions = [objective("zombie_action", "kill", "minecraft:zombie", 2, "本轮击败僵尸"),
               objective("skeleton_action", "kill", "minecraft:skeleton", 2, "本轮击败骷髅"),
               objective("spider_action", "kill", "minecraft:spider", 1, "本轮击败蜘蛛"),
               objective("logs_action", "collect", "minecraft:logs", 8, "本轮获得原木", "minecraft:logs"),
               objective("coal_action", "collect", "minecraft:coal", 4, "本轮获得煤炭"),
               objective("iron_action", "craft", "minecraft:iron_ingot", 2, "本轮用铁粒合成铁锭")]
run_bindings = [binding(name, name, (f"{name}_action",)) for name in ("zombie", "skeleton", "spider", "logs", "coal")]
run_bindings.append(binding("iron", "iron_ingot", ("iron_action",)))
preparation = phase("preparation", "调查准备", [objective("fuel", "collect", "minecraft:coal", 1, "获得准备燃料")], [binding("fuel", "coal", ("fuel",))], targets=("wildlife", "materials"))
wildlife = phase("wildlife", "生物调查", run_actions[:3], run_bindings[:3], quota=2, targets=("report",))
wildlife["flagsToSetOnComplete"] = ["json_demo_wildlife_done"]
materials = phase("materials", "材料调查", run_actions[3:], run_bindings[3:], quota=2, targets=("report",))
materials["flagsToSetOnComplete"] = ["json_demo_materials_done"]
report = phase("report", "提交调查样本", [objective("submit_logs", "offer", "minecraft:logs", 4, "提交原木", "minecraft:logs")], [binding("logs", "logs", ("submit_logs",))], manual=True)
report["enterCondition"] = {"condition": "arc_quest:and", "conditions": [{"condition": "arc_quest:has_flag", "flag": "json_demo_wildlife_done"}, {"condition": "arc_quest:has_flag", "flag": "json_demo_materials_done"}]}

documents = {
    "field_compendium_demo": quest("field_compendium_demo", "荒野手册 · JSON", [phase("survey", "林地调查", field_actions, field_bindings, manual=True)]),
    "renewable_survey_demo": quest("renewable_survey_demo", "轮值委托 · JSON", [phase("round", "本轮调查", run_actions, run_bindings, quota=3, manual=True)], repeat=True),
    "parallel_expedition_demo": quest("parallel_expedition_demo", "联合调查 · JSON", [preparation, wildlife, materials, report]),
}
field_config = documents["field_compendium_demo"]["collectionConfig"]
field_config["rewardNodes"] = [{
    "nodeId": "field_three_samples", "scope": "QUEST", "grantMode": "AUTO",
    "scopeRefId": f"{NS}:field_compendium_demo",
    "completionRules": [{"type": "completed_entry_count", "value": 3}],
    "rewards": [{"type": "item", "itemId": "minecraft:coal", "count": 1}],
}]
field_config["categories"][0]["rewardNodes"] = [{
    "nodeId": "field_living_complete", "scope": "CATEGORY", "grantMode": "MANUAL", "scopeRefId": "living",
    "completionRules": [{"type": "all_entries_complete"}],
    "rewards": [{"type": "item", "itemId": "minecraft:emerald", "count": 1}],
}]
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
