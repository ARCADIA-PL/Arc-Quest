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
def binding(key, name, objectives=(), record=None, policy="EXISTING_RECORDS", outcome=None, rewards=()):
    result = {"bindingId": key, "entryId": eid(name), "objectiveIds": list(objectives),
              "requirementMode": "ALL", "recordPolicy": policy}
    if record: result["recordRequirements"] = record
    if outcome:
        result["outcomeIds"] = [outcome]
        result.setdefault("recordRequirements", discovered())
    if rewards: result["rewards"] = list(rewards)
    return result
def discovered(): return [{"type": "DISCOVERED"}]
def reward(key, item, count, trigger="BINDING_COMPLETE", outcome=None, mode="MANUAL"):
    result = {"rewardId": key, "trigger": trigger, "grantMode": mode, "previewVisibility": "PUBLIC",
              "rewards": [{"type": "item", "itemId": f"minecraft:{item}", "count": count}]}
    if outcome: result["outcomeId"] = outcome
    return result
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
             "displayName": text(title), "description": text(f"{title}调查资料：发现与本次行动分别保存，档案成果来自明确调查。"),
             "gameplayVersion": 2,
             "subjectKind": kind, "discoveryObjectives": [objective("first_record", action, f"minecraft:{target}", 1, f"发现{title}", "minecraft:logs" if name == "logs" else None)],
             "content": [{"blockId": "notes", "text": text(f"观察{title}并记录样本。点击物品图标可以查询 JEI。"), "reveal": "DISCOVERED"}],
             "visibilityMode": "VISIBLE_BY_DEFAULT", "hiddenPresentationMode": "FULLY_HIDDEN", "sortOrder": len(entries)}
    if name == "logs": entry["itemTag"] = "minecraft:logs"
    else: entry["subjectId"] = f"minecraft:{target}"
    if research:
        outcomes = {"zombie": ("anatomy", "解剖记录"), "skeleton": ("combat", "战斗记录"), "spider": ("samples", "蛛丝样本"),
                    "iron_ingot": ("preparation", "制备记录"), "coal": ("fuel_samples", "燃料样本"), "logs": ("wood_samples", "木材样本")}
        outcome_id, outcome_name = outcomes[name]
        entry["outcomes"] = [{"outcomeId": outcome_id, "displayName": text(outcome_name)}]
        entry["legacyResearchObjectives"] = [objective("study", "craft" if name == "iron_ingot" else action,
                                                 f"minecraft:{target}", research, f"旧版研究{title}", "minecraft:logs" if name == "logs" else None)]
        entry["legacyResearchOutcomeMappings"] = {"study": outcome_id}
    if name == "spider": entry.update(visibilityMode="HIDDEN_BY_DEFAULT", hiddenPresentationMode="PLACEHOLDER",
                                        publicClue=text("夜间寻找会攀爬墙面的八足生物，击败一只并提交两份线样本。"))
    if name in ("zombie", "iron_ingot"):
        entry["content"].append({"blockId": "field_image", "media": {"type": "image", "texture": f"arc_quest:textures/gui/collection/{'field' if name == 'zombie' else 'mineral'}_notes.png", "width": 240, "height": 120},
                                 "caption": text("调查配图，点击可放大"), "fit": "CONTAIN", "zoomable": True, "reveal": "DISCOVERED"})
    if name == "zombie":
        entry["rewards"] = [reward("zombie_first_record", "coal", 1, "DISCOVERED"),
                            reward("zombie_anatomy", "iron_nugget", 3, "OUTCOME", "anatomy")]
        entry["relatedItems"] = ["minecraft:rotten_flesh"]
        entry["content"].append({"blockId": "anatomy", "text": text("解剖记录：三次击败及两份腐肉样本共同完成调查，不再另算永久击杀。"), "reveal": "OUTCOME", "revealStepId": "anatomy"})
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
                 objective("spider_defeat", "kill", "minecraft:spider", 1, "击败蜘蛛"),
                 objective("spider_samples", "offer", "minecraft:string", 2, "提交线样本"),
                 objective("iron_crafting", "craft", "minecraft:iron_ingot", 1, "用铁粒合成铁锭"),
                 objective("coal_samples", "offer", "minecraft:coal", 5, "提交煤炭样本"),
                 objective("logs_action", "offer", "minecraft:logs", 8, "提交任意原木共八个", "minecraft:logs")]
field_bindings = [binding("zombie", "zombie", ("zombie_defeats", "zombie_samples"), outcome="anatomy", rewards=[reward("zombie_investigation", "emerald", 1)]),
                  binding("skeleton", "skeleton", ("skeleton_defeats",), outcome="combat"),
                  binding("spider", "spider", ("spider_defeat", "spider_samples"), outcome="samples"),
                  binding("cow", "cow", record=discovered()), binding("iron", "iron_ingot", ("iron_crafting",), outcome="preparation"),
                  binding("coal", "coal", ("coal_samples",), outcome="fuel_samples"),
                  binding("logs", "logs", ("logs_action",), outcome="wood_samples", rewards=[reward("logs_investigation", "stick", 2, mode="AUTO")]),
                  binding("bone", "bone", record=discovered())]

run_actions = [objective("zombie_action", "kill", "minecraft:zombie", 2, "本轮击败僵尸"),
               objective("skeleton_action", "kill", "minecraft:skeleton", 2, "本轮击败骷髅"),
               objective("spider_action", "kill", "minecraft:spider", 1, "本轮击败蜘蛛"),
               objective("logs_action", "offer", "minecraft:logs", 8, "本轮提交原木", "minecraft:logs"),
               objective("coal_action", "offer", "minecraft:coal", 4, "本轮提交煤炭"),
               objective("iron_action", "offer", "minecraft:iron_ingot", 1, "本轮提交铁锭")]
run_bindings = [binding(name, name, (f"{name}_action",)) for name in ("zombie", "skeleton", "spider", "logs", "coal")]
run_bindings.append(binding("iron", "iron_ingot", ("iron_action",)))
preparation = phase("preparation", "调查准备", [objective("fuel", "offer", "minecraft:coal", 1, "提交准备燃料")], [binding("fuel", "coal", ("fuel",))], targets=("wildlife", "materials"))
wildlife = phase("wildlife", "生物调查", run_actions[:3], run_bindings[:3], quota=2, targets=("report",))
materials = phase("materials", "材料调查", run_actions[3:], run_bindings[3:], quota=2, targets=("report",))
report = phase("report", "提交调查样本", [objective("submit_logs", "offer", "minecraft:logs", 4, "提交原木", "minecraft:logs")], [binding("logs", "logs", ("submit_logs",))], manual=True)
report["enterCondition"] = {"condition": "arc_quest:and", "conditions": [
    {"condition": "arc_quest:quest_phase_completed_current_run", "questId": f"{NS}:parallel_expedition_demo", "phaseId": phase_id}
    for phase_id in ("wildlife", "materials")]}

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
