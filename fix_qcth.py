import os

fp = 'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/capability/QuestCapabilityTickHandler.java'
with open(fp, 'r', encoding='utf-8') as f:
    content = f.read()
orig = content

# imports
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.IQuestCapability;',
    'import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;')
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;',
    'import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;')
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.QuestCapabilityImpl;', '')

# IQuestCapability -> ArcQuestPlayer for type references
content = content.replace('IQuestCapability cap', 'ArcQuestPlayer playerData')
content = content.replace('IQuestCapability', 'ArcQuestPlayer')

# player.getCapability(QUEST_CAP).ifPresent(cap -> {
# → ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player); if (playerData != null) {
content = content.replace(
    'player.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {',
    '{\n            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);\n            if (playerData != null) {')

# Fix the closing of lambda: }); → }} (close if + close block)
content = content.replace(
    '        });',
    '            }\n        }')

# cap.xxx -> playerData.xxx inside the lambda body
for m in ['getAllActiveQuests', 'markFailed', 'removeActiveQuest', 'upsertMarker',
          'removeMarker', 'isQuestCompleted', 'getCompletedQuestLocations',
          'hasFlag', 'getAllFlags', 'getAllVariables', 'getVariable',
          'isQuestActive', 'getCompletedQuests', 'getFailedQuests']:
    content = content.replace('cap.' + m, 'playerData.' + m)

# instanceof check
content = content.replace('playerData instanceof QuestCapabilityImpl impl', 'playerData != null')
content = content.replace('QuestSyncCoordinator.syncIfChanged(player, impl)',
                          'QuestSyncCoordinator.syncIfChanged(player, playerData)')
content = content.replace('QuestSyncCoordinator.persistAndSyncIfChanged(player, impl)',
                          'QuestSyncCoordinator.persistAndSyncIfChanged(player, playerData)')
content = content.replace('impl.getDirtyKind', 'playerData.getDirtyKind')
content = content.replace('impl.serializeNBT', 'playerData.serializeNBT')
content = content.replace('impl.clearDirty', 'playerData.clearDirty')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(content)
print('OK' if content != orig else 'NOCHANGE')
