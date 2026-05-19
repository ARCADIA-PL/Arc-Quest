import os

files = [
    # Group J - conditions (simple)
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/condition/ConditionBridge.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/condition/ConditionEvaluator.java',
    # Group H - Quest logic
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/service/QuestOfferService.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/reward/FlagReward.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/reward/VariableReward.java',
    # Group I - Collection (check QuestRuntimeData conflicts)
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionObjectiveDispatcher.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/logic/profile/collection/CollectionVisibilityResolver.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/logic/profile/CollectionQuestEngine.java',
    # More Group H
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/tracking/QuestEventManager.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/logic/QuestMarkerService.java',
]
for fp in files:
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    orig = content
    # import replacements
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.IQuestCapability;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;')
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;')
    # specific patterns
    content = content.replace('QuestCapabilityProvider.getOrNull(player)', 'ArcQuestPlayerManager.get(player)')
    content = content.replace('IQuestCapability cap', 'ArcQuestPlayer data')
    content = content.replace(', IQuestCapability', ', ArcQuestPlayer')
    content = content.replace('(IQuestCapability', '(ArcQuestPlayer')
    # cap.xxx -> data.xxx (only safe replacements)
    for m in ['getAllFlags', 'getAllVariables', 'hasFlag', 'getVariable',
              'setVariable', 'incrementVariable', 'setFlag', 'getCapability',
              'isQuestActive', 'isQuestCompleted', 'isQuestFailed',
              'getActiveQuest', 'getCompletedQuestLocations',
              'addActiveQuest', 'markCompleted', 'markFailed',
              'getAllActiveQuests', 'getCompletedQuests', 'getFailedQuests',
              'serializeNBT', 'upsertMarker', 'removeMarker',
              'getDialogueProgress', 'getGachaDataStore', 'getTradeDataStore',
              'isCollectionQuestActive', 'getCollectionData']:
        content = content.replace('cap.' + m, 'data.' + m)
    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print('OK:', os.path.basename(fp))
    else:
        print('NOCHANGE:', os.path.basename(fp))
