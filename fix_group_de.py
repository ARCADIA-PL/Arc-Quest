import os, re

files = [
    # Group D - Trade
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/api/TradeShopDefinition.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/runtime/TradeSession.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/network/TradeRequestValidator.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/offer/FlagTradeOffer.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/runtime/TradeEntryStateResolver.java',
    # Group E - Gacha
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/api/GachaShopDefinition.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/api/GachaPool.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/api/GachaItem.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/api/event/gacha/GachaEvents.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/runtime/GachaSession.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/runtime/GachaEntryStateResolver.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/GachaRequestValidator.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/PendingDrawManager.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/runtime/GachaScreenOpener.java',
]

for fp in files:
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    orig = content

    # 1. Imports
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.IQuestCapability;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;')
    content = content.replace(
        'import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;',
        'import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;')

    # 2. GachaDrawRecord
    content = content.replace('IQuestCapability.GachaDrawRecord', 'ArcQuestPlayer.GachaDrawRecord')

    # 3. QuestCapabilityProvider.getOrNull → ArcQuestPlayerManager.get
    content = content.replace(
        'QuestCapabilityProvider.getOrNull(player)',
        'ArcQuestPlayerManager.get(player)')

    # 4. TradeSession: IQuestCapability getCap() → ArcQuestPlayer getData()
    content = content.replace(
        'private IQuestCapability getCap() {',
        'private ArcQuestPlayer getData() {')
    content = content.replace(
        'return QuestCapabilityProvider.getOrNull(player);',
        'return ArcQuestPlayerManager.get(player);')

    # 5. Method parameter types: IQuestCapability → ArcQuestPlayer
    # (preserve the variable name 'cap' for now, will rename later if needed)
    content = re.sub(r'(?<!\w)IQuestCapability(?!\.)\s+cap\b', 'ArcQuestPlayer data', content)
    content = re.sub(r'(?<!\w)IQuestCapability(?!\.)\s+capability\b', 'ArcQuestPlayer playerData', content)
    content = content.replace('IQuestCapability capability', 'ArcQuestPlayer playerData')
    content = content.replace(', IQuestCapability ', ', ArcQuestPlayer ')
    content = content.replace('(IQuestCapability ', '(ArcQuestPlayer ')
    content = content.replace('@Nullable IQuestCapability', '@Nullable ArcQuestPlayer')

    # 6. GachaRequestValidator/TradeRequestValidator: requireCapability → requireData
    content = content.replace(
        'public static @Nullable IQuestCapability requireCapability',
        'public static @Nullable ArcQuestPlayer requireData')
    content = content.replace(
        'IQuestCapability resolved =', 'ArcQuestPlayer resolved =')
    content = content.replace(
        'return requireCapability', 'return requireData')
    content = content.replace(
        'IQuestCapability required =', 'ArcQuestPlayer required =')

    # 7. Any remaining bare IQuestCapability (should be few)
    content = content.replace('IQuestCapability', 'ArcQuestPlayer')

    # 8. Safe cap.xxx → data.xxx replacements
    for m in ['getTradeDataStore', 'getGachaDataStore', 'getDialogueProgress',
              'getAllFlags', 'getAllVariables', 'hasFlag', 'getVariable',
              'setVariable', 'incrementVariable', 'setFlag', 'getCapability',
              'isQuestActive', 'isQuestCompleted', 'isQuestFailed',
              'getActiveQuest', 'getCompletedQuestLocations', 'getAllActiveQuests',
              'addActiveQuest', 'markCompleted', 'markFailed', 'getCompletedQuests',
              'serializeNBT', 'isCollectionQuestActive', 'getCollectionData',
              'getGachaDrawCount', 'incrementGachaDrawCount', 'resetGachaDrawCount',
              'getGachaPityCounter', 'setGachaPityCounter',
              'addGachaDrawHistory', 'getGachaDrawHistory', 'clearGachaDrawHistory']:
        content = content.replace('cap.' + m, 'data.' + m)

    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print('OK:', os.path.basename(fp))
    else:
        print('NOCHANGE:', os.path.basename(fp))
