import os

files = [
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/runtime/DialogueSession.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/runtime/DialogueEvalContext.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/api/DialogueCondition.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/api/DialogueAction.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/runtime/ConditionalTextEvaluator.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/runtime/DialogueSessionManager.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/api/IDialogueNpc.java',
]

for fp in files:
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
        'import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatch;',
        'import org.arcadia.arc_quest.dialogue.capability.DialogueNpcStateManager;')
    content = content.replace(
        'import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatchProvider;', '')

    # types
    content = content.replace('QuestCapabilityProvider.getOrNull(player)', 'ArcQuestPlayerManager.get(player)')
    content = content.replace('IQuestCapability cap', 'ArcQuestPlayer data')
    content = content.replace('IQuestCapability questCap', 'ArcQuestPlayer questData')
    content = content.replace('IQuestCapability', 'ArcQuestPlayer')
    content = content.replace('DialogueNpcPatch.get(', 'DialogueNpcStateManager.get(')
    content = content.replace('DialogueNpcPatch.CAPABILITY', '')

    # cap.xxx → data.xxx
    for m in ['getAllFlags', 'getAllVariables', 'hasFlag', 'getVariable',
              'setVariable', 'incrementVariable', 'setFlag',
              'isQuestActive', 'isQuestCompleted', 'isQuestFailed',
              'getActiveQuest', 'getCompletedQuestLocations', 'getAllActiveQuests',
              'addActiveQuest', 'markCompleted', 'markFailed', 'getCompletedQuests',
              'getFailedQuests', 'serializeNBT',
              'getDialogueProgress', 'getGachaDataStore', 'getTradeDataStore']:
        content = content.replace('cap.' + m, 'data.' + m)

    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print('OK:', os.path.basename(fp))
    else:
        print('NOCHANGE:', os.path.basename(fp))
