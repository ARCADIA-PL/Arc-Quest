import os

files = [
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/network/C2SRequestQuestActionPacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/quest/network/C2SClaimCollectionRewardPacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/network/C2SRequestTradePacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/C2SGachaControlPacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/C2SDrawGachaPacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/C2SConfirmDrawPacket.java',
    'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/trade/gacha/network/S2CGachaStatePacket.java',
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
    # IQuestCapability.GachaDrawRecord -> ArcQuestPlayer.GachaDrawRecord
    content = content.replace('IQuestCapability.GachaDrawRecord', 'ArcQuestPlayer.GachaDrawRecord')
    # IQuestCapability (remaining bare references as type) -> ArcQuestPlayer
    content = content.replace('IQuestCapability cap', 'ArcQuestPlayer data')
    content = content.replace('(IQuestCapability ', '(ArcQuestPlayer ')
    content = content.replace(', IQuestCapability', ', ArcQuestPlayer')
    content = content.replace('@Nullable IQuestCapability', '@Nullable ArcQuestPlayer')
    content = content.replace('private IQuestCapability', 'private ArcQuestPlayer')
    content = content.replace('public IQuestCapability', 'public ArcQuestPlayer')
    content = content.replace('QuestCapabilityProvider.getOrNull(player)', 'ArcQuestPlayerManager.get(player)')
    if content != orig:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print('OK:', os.path.basename(fp))
    else:
        print('NOCHANGE:', os.path.basename(fp))
