import os

fp = 'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/logic/QuestProgressHandler.java'
with open(fp, 'r', encoding='utf-8') as f:
    content = f.read()
orig = content

# 1. imports
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.IQuestCapability;',
    'import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;')
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;',
    'import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;')

# 2. simple assignment: IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
content = content.replace(
    'IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);',
    'ArcQuestPlayer data = ArcQuestPlayerManager.get(player);')

# 3. method sigs with conflict: IQuestCapability cap,\n   QuestRuntimeData data
content = content.replace(
    'IQuestCapability cap,\n                                             QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                             QuestRuntimeData qdata,')

content = content.replace(
    'IQuestCapability cap,\n                                      QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                      QuestRuntimeData qdata,')

content = content.replace(
    'IQuestCapability cap,\n                                        QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                        QuestRuntimeData qdata,')

content = content.replace(
    'IQuestCapability cap,\n                                                          QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                                          QuestRuntimeData qdata,')

content = content.replace(
    'IQuestCapability cap,\n                                         QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                         QuestRuntimeData qdata,')

content = content.replace(
    'IQuestCapability cap,\n                                          QuestRuntimeData data,',
    'ArcQuestPlayer data,\n                                          QuestRuntimeData qdata,')

# 4. remaining IQuestCapability cap, (no QuestRuntimeData conflict)
content = content.replace('IQuestCapability cap,', 'ArcQuestPlayer data,')

# 5. syncFlagsVarsAndPush, syncFullDataAndPush, rebuildTrackingIndex signatures
content = content.replace(
    'private static void syncFlagsVarsAndPush(ServerPlayer player, IQuestCapability cap)',
    'private static void syncFlagsVarsAndPush(ServerPlayer player, ArcQuestPlayer data)')
content = content.replace(
    'private static void syncFullDataAndPush(ServerPlayer player, IQuestCapability cap)',
    'private static void syncFullDataAndPush(ServerPlayer player, ArcQuestPlayer data)')
content = content.replace(
    'public static void rebuildTrackingIndex(ServerPlayer player, IQuestCapability cap)',
    'public static void rebuildTrackingIndex(ServerPlayer player, ArcQuestPlayer data)')
content = content.replace(
    'public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, IQuestCapability cap)',
    'public static int resolveRequiredCount(ServerPlayer player, ObjectiveEntry obj, ArcQuestPlayer data)')

# 6. remaining cap.xxx -> data.xxx where cap refers to ArcQuestPlayer
# (only for patterns where cap is confirmed to be ArcQuestPlayer)
for m in ['isQuestActive', 'isQuestCompleted', 'getAllActiveQuests', 'getCompletedQuests',
          'hasFlag', 'getAllFlags', 'getCompletedQuestLocations', 'setFlag',
          'addActiveQuest', 'markCompleted', 'markFailed', 'removeActiveQuest',
          'getDialogueProgress', 'getTradeDataStore', 'getGachaDataStore',
          'getVariable', 'setVariable', 'incrementVariable',
          'upsertMarker', 'removeMarker', 'clearMarkers', 'getAllMarkers',
          'getCompletedQuestIds', 'getCollectionData', 'isCollectionQuestActive',
          'serializeNBT', 'getActiveQuest', 'getFailedQuests', 'copyFrom',
          'isQuestFailed', 'getAllVariables', 'removeFlag']:
    content = content.replace('cap.' + m, 'data.' + m)

# 7. Remaining bare IQuestCapability references (any type ref)
content = content.replace('IQuestCapability', 'ArcQuestPlayer')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(content)
print('OK' if content != orig else 'NOCHANGE')
