"""Read the deliberately small builder DSL used by CollectionFieldDemos.

This is a demo generator, not a general Java parser. Unknown builder methods fail
generation. Java and installable JSON examples therefore share the same playable
rules, text, content and reward amounts; compiled parity is checked by JUnit.
"""
from pathlib import Path
import json
import re


def build_documents(root: Path, namespace: str):
    source = (root / "src/main/java/org/arcadia/arc_quest/quest/registry/CollectionFieldDemos.java").read_text(encoding="utf-8")
    aliases = {name: "arc_quest:" + path for name, path in re.findall(
        r'public static final ResourceLocation (\w+) = id\("([^"]+)"\);', source)}
    aliases.update({name: value for name, value in re.findall(
        r'private static final ResourceLocation (\w+) = ResourceLocation.parse\("([^"]+)"\);', source)})

    def balanced_end(value, start):
        depth, quoted, escaped = 0, False, False
        for index in range(start, len(value)):
            char = value[index]
            if quoted:
                if escaped: escaped = False
                elif char == "\\": escaped = True
                elif char == '"': quoted = False
            elif char == '"': quoted = True
            elif char == "(": depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0: return index
        raise ValueError("Unbalanced demo builder expression: " + value[start:])

    def args(value):
        result, start, depth, quoted, escaped = [], 0, 0, False, False
        for index, char in enumerate(value):
            if quoted:
                if escaped: escaped = False
                elif char == "\\": escaped = True
                elif char == '"': quoted = False
            elif char == '"': quoted = True
            elif char == "(": depth += 1
            elif char == ")": depth -= 1
            elif char == "," and depth == 0:
                result.append(value[start:index].strip()); start = index + 1
        if value.strip(): result.append(value[start:].strip())
        return result

    def calls(value):
        result, offset = [], 0
        while match := re.search(r'(\w+)\s*\(', value[offset:]):
            start = offset + match.end() - 1
            end = balanced_end(value, start)
            result.append((match.group(1), args(value[start + 1:end])))
            offset = end + 1
        return result

    def literal(value): return json.loads(value)
    def text(value):
        if value.startswith('"'): return {"mode": "literal", "value": literal(value)}
        expression = calls(value)
        if len(expression) == 1 and expression[0][0] == "t":
            return {"mode": "translatable", "value": "arc_quest.collection.demo." + literal(expression[0][1][0])}
        raise ValueError("Unsupported demo text: " + value)
    def resource(value):
        if value in aliases: return aliases[value]
        if value.startswith('"'): return literal(value)
        if value.startswith("Items.") or value.startswith("EntityType."):
            return "minecraft:" + value.split(".")[1].lower()
        expression = calls(value)
        if len(expression) == 1 and expression[0][0] in ("parse", "id"):
            name, values = expression[0]
            return ("arc_quest:" if name == "id" else "") + literal(values[0])
        raise ValueError("Unsupported demo resource: " + value)

    def item_reward(value):
        name, values = calls(value)[0]
        if name != "ItemReward": raise ValueError("Unsupported demo reward: " + value)
        return {"type": "item", "itemId": resource(values[0]), "count": int(values[1])}

    def reward(key, payloads, trigger, outcome="", mode="MANUAL"):
        result = {"rewardId": key, "trigger": trigger, "grantMode": mode,
                  "previewVisibility": "PUBLIC", "rewards": [item_reward(v) for v in payloads]}
        if outcome: result["outcomeId"] = outcome
        return result

    def objective(value):
        chain = calls(value)
        kind, values = chain.pop(0)
        if kind not in ("kill", "collect", "collectTag", "interact", "offer", "offerTag", "craft", "possess"):
            raise ValueError("Unsupported demo objective: " + kind)
        target = resource(values[0])
        result = {"type": "arc_quest:" + kind.removesuffix("Tag").replace("possess", "collect"),
                  "targetId": target, "requiredCount": int(values[1]) if len(values) > 1 else 1}
        if kind.endswith("Tag"): result["itemTag"] = target
        if kind == "possess": result["collectMode"] = "POSSESSION"
        for name, values in chain:
            if name == "id": result["id"] = literal(values[0])
            elif name == "display": result["displayText"] = text(values[0])
            elif name != "build": raise ValueError("Unsupported demo objective method: " + name)
        if "displayText" not in result:
            raise ValueError("Demo objectives must explicitly configure translated display text: " + value)
        return result

    entries = []
    for expression in re.findall(r'var \w+ = (CollectionEntryBuilder\.create\(.+?\.build\(\));', source, re.S):
        entry = {"gameplayVersion": 2, "content": [], "outcomes": [], "rewards": [], "relatedItems": [],
                 "discoveryObjectives": [], "legacyResearchObjectives": [], "legacyResearchOutcomeMappings": {},
                 "visibilityMode": "VISIBLE_BY_DEFAULT", "hiddenPresentationMode": "FULLY_HIDDEN", "sortOrder": 0}
        for name, values in calls(expression):
            if name == "create": entry["entryId"] = resource(values[0])
            elif name == "category": entry["categoryId"] = literal(values[0])
            elif name in ("displayName", "description", "publicClue"): entry[name] = text(values[0])
            elif name in ("entity", "item"):
                entry["subjectKind"] = "ENTITY" if name == "entity" else "ITEM"
                entry["subjectId"] = resource(values[0])
            elif name == "itemTag": entry.update(subjectKind="ITEM", itemTag=resource(values[0]))
            elif name == "discover": entry["discoveryObjectives"].append(objective(values[0]))
            elif name == "outcome": entry["outcomes"].append({"outcomeId":literal(values[0]), "displayName":text(values[1])})
            elif name == "migrateResearchStep":
                old = objective(values[0]); entry["legacyResearchObjectives"].append(old)
                entry["legacyResearchOutcomeMappings"][old["id"]] = literal(values[1])
            elif name == "discoveryReward": entry["rewards"].append(reward(literal(values[0]), values[1:], "DISCOVERED"))
            elif name == "outcomeReward": entry["rewards"].append(reward(literal(values[1]), values[2:], "OUTCOME", literal(values[0])))
            elif name == "relatedItem": entry["relatedItems"].append(resource(values[0]))
            elif name == "text": entry["content"].append({"blockId":literal(values[0]), "text":text(values[1]), "reveal":"DISCOVERED"})
            elif name == "image": entry["content"].append({"blockId":literal(values[0]), "text":text('""'),
                "media":{"type":"image", "texture":resource(values[1]), "width":int(values[2]), "height":int(values[3])},
                "caption":text(values[4]), "fit":"CONTAIN", "zoomable":True, "reveal":"DISCOVERED"})
            elif name == "content":
                helper, block = calls(values[0])[0]
                if helper != "outcomeNotes": raise ValueError("Unsupported demo content: " + helper)
                entry["content"].append({"blockId":literal(block[0]), "text":text(block[2]), "reveal":"OUTCOME", "revealStepId":literal(block[1]), "zoomable":False})
            elif name == "visibility": entry.update(visibilityMode=values[0].split(".")[1], hiddenPresentationMode=values[1].split(".")[1])
            elif name == "sortOrder": entry["sortOrder"] = int(values[0])
            elif name != "build": raise ValueError("Unsupported demo entry method: " + name)
        entries.append(entry)
    if len(entries) != 13: raise ValueError(f"Expected thirteen shared demo entries; parsed {len(entries)}")

    def binding(value):
        result = {"objectiveIds":[], "recordRequirements":[], "outcomeIds":[], "rewards":[], "requirementMode":"ALL"}
        for name, values in calls(value):
            if name == "create": result.update(bindingId=literal(values[0]), entryId=resource(values[1]))
            elif name in ("objective", "objectives"): result["objectiveIds"].extend(literal(v) for v in values)
            elif name == "recordOutcome":
                result["outcomeIds"].append(literal(values[0]))
                if not result["recordRequirements"]: result["recordRequirements"].append({"type":"DISCOVERED"})
            elif name == "reward":
                mode = values[1].split(".")[1] if values[1].startswith("EntryRewardGrantMode.") else "MANUAL"
                result["rewards"].append(reward(literal(values[0]), values[2:] if mode == "AUTO" else values[1:], "BINDING_COMPLETE", mode=mode))
            else: raise ValueError("Unsupported demo binding method: " + name)
        return result

    def phase(value, quest_id):
        result = {"objectives":[], "autoAdvanceOnComplete":True}
        for name, values in calls(value):
            if name == "create": result["phaseId"] = literal(values[0])
            elif name in ("displayName", "description"): result[name] = text(values[0])
            elif name == "objective": result["objectives"].append(objective(values[0]))
            elif name == "collectionSheet":
                sheet = {"bindings":[], "completionPolicy":"ALL", "requiredCount":0, "countDistinctEntries":False}
                for method, fields in calls(values[0]):
                    if method == "binding": sheet["bindings"].append(binding(fields[0]))
                    elif method == "quota": sheet.update(completionPolicy="QUOTA", requiredCount=int(fields[0]))
                    elif method != "create": raise ValueError("Unsupported demo sheet method: " + method)
                result["collectionSheet"] = sheet
            elif name == "autoAdvanceOnComplete": result[name] = literal(values[0])
            elif name == "thenGoTo": result.setdefault("transitions", []).append({"targetPhaseIds":[literal(v) for v in values]})
            elif name == "enterWhen":
                if values != ["both"]: raise ValueError("Unknown camp join condition")
                result["enterCondition"] = {"condition":"arc_quest:and", "conditions":[
                    {"condition":"arc_quest:quest_phase_completed_current_run", "questId":quest_id, "phaseId":p}
                    for p in ("wildlife", "materials")]}
            else: raise ValueError("Unsupported demo phase method: " + name)
        return result

    categories = [{"categoryId":key, "displayName":text(f't("category.{key}")')} for key in
                  ("living", "materials", "equipment")]
    documents = {}
    for method, key in (("field", "field_compendium_demo"), ("renewable", "renewable_survey_demo"), ("parallel", "parallel_expedition_demo")):
        body = source.split(f"public static QuestDefinition {method}(", 1)[1].split("\n    private static", 1)[0].split("\n    public static", 1)[0]
        expression = body[body.index("return base("):]
        quest_id = "arc_quest:" + key
        document = {"id":quest_id, "category":"arc_quest:collection", "mode":"COLLECTION", "repeatable":False,
                    "visualConfig":{"themeColor":0x85C6AE}, "phases":[], "completionPolicy":"ALL", "completionRewards":[],
                    "collectionConfig":{"categories":json.loads(json.dumps(categories)), "entries":json.loads(json.dumps(entries))}}
        for name, values in calls(expression):
            if name == "base": document.update(displayName=text(values[1]), description=text(values[2]))
            elif name == "phase": document["phases"].append(phase(values[0], quest_id))
            elif name == "reward": document["completionRewards"].append(item_reward(values[0]))
            elif name == "sortOrder": document["sortOrder"] = int(values[0])
            elif name == "repeatable": document["repeatable"] = True
            elif name not in ("collectionConfig", "build"): raise ValueError("Unsupported demo quest method: " + name)
        selected = set(resource(value) for value in args(re.search(r'entries = entriesFor\(entries, (.+?)\);', body).group(1)))
        document["collectionConfig"]["entries"] = [entry for entry in document["collectionConfig"]["entries"] if entry["entryId"] in selected]
        used_categories = {entry["categoryId"] for entry in document["collectionConfig"]["entries"]}
        document["collectionConfig"]["categories"] = [category for category in document["collectionConfig"]["categories"] if category["categoryId"] in used_categories]
        for index, category in enumerate(document["collectionConfig"]["categories"]): category["sortOrder"] = index
        document["initialPhaseId"] = document["phases"][0]["phaseId"]
        if method == "renewable": document["collectionConfig"]["repeatCooldownTicks"] = 1200
        if method == "field":
            document["collectionConfig"]["rewardNodes"] = [{"nodeId":"field_three_samples", "scope":"QUEST", "grantMode":"AUTO", "scopeRefId":quest_id,
                "completionRules":[{"type":"completed_entry_count", "value":3}], "rewards":[{"type":"item", "itemId":"minecraft:coal", "count":1}]}]
            document["collectionConfig"]["categories"][0]["rewardNodes"] = [{"nodeId":"field_living_complete", "scope":"CATEGORY", "grantMode":"MANUAL", "scopeRefId":"living",
                "completionRules":[{"type":"all_entries_complete"}], "rewards":[{"type":"item", "itemId":"minecraft:emerald", "count":1}]}]
        # Remap only quest/entry identities, never ArcQ types, categories or texture resources.
        def remap(value):
            if isinstance(value, dict): return {k:remap(v) for k,v in value.items()}
            if isinstance(value, list): return [remap(v) for v in value]
            if isinstance(value, str) and (value.startswith("arc_quest:codex/") or value in (
                    "arc_quest:field_compendium_demo", "arc_quest:renewable_survey_demo", "arc_quest:parallel_expedition_demo")):
                return namespace + value[len("arc_quest"):]
            return value
        documents[key] = remap(document)
    return documents
