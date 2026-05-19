import os

files = [
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/command/GachaCommands.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/command/DialogueCommands.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/command/QuestCommands.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/command/MarkerTestCommand.java',
]
for fp in files:
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    orig = content
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;')
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.IQuestCapability;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;')
    content = content.replace(
        'IQuestCapability getCap(ServerPlayer player)',
        'ArcQuestPlayer getData(ServerPlayer player)')
    content = content.replace(
        'return QuestCapabilityProvider.getOrNull(player);',
        'return ArcQuestPlayerManager.get(player);')
    content = content.replace(
        'IQuestCapability cap = getCap(player);',
        'ArcQuestPlayer data = getData(player);')
    content = content.replace(
        'var cap = QuestCapabilityProvider.getOrNull(player);',
        'var data = ArcQuestPlayerManager.get(player);')
    for m in ['getGachaDataStore', 'getTradeDataStore', 'getDialogueProgress',
              'getAllFlags', 'getAllVariables', 'hasFlag', 'getVariable',
              'setVariable', 'incrementVariable', 'setFlag',
              'isQuestActive', 'isQuestCompleted', 'isQuestFailed',
              'getActiveQuest', 'getCompletedQuestLocations',
              'addActiveQuest', 'markCompleted', 'markFailed',
              'getAllActiveQuests', 'getCompletedQuests', 'serializeNBT']:
        content = content.replace('cap.' + m, 'data.' + m)
    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print('OK:', os.path.basename(fp))
    else:
        print('NOCHANGE:', os.path.basename(fp))
