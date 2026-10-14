# ArcQ 1.0.10 lifecycle and delivery changes

Framework failures are repaired here, with native APIs for addons.

- Capability invalidation retires every retained LazyOptional; dispatcher revival can query a fresh handle to the same capability data.
- QuestRuntimeData owns copies of progress arrays on NBT save and load. A later progress update cannot mutate an earlier rollback snapshot.
- Custom dialogue handlers propagate errors to DialogueActionExecutor. Missing handlers fail, and a failed chain ends before further rewards or node transitions.
- ItemTradeOffer rewards and DialogueAction.GiveItem require atomic main-inventory delivery, including creative players. A full inventory rejects the reward; native trade mutations roll back costs and preceding inventory changes. Dynamic trade counts resolve once at preparation; stack NBT is retained.
- Permanent possession discovery samples actual inventory during play, including container/shop transfers, without inventing acquisition or research events.
- Guide media supports declared ITEM stacks and IMAGE CONTAIN sizing; texture icons use GuideBuilder.iconTexture. Addons do not need to intercept GuiGraphics or maintain fake texture-to-item maps.

Compatibility: full-inventory item trades/dialogue rewards now fail rather than dropping the reward or letting creative Inventory.add consume it. Addons that execute actions directly must handle failures. The old media constructors preserve COVER image behavior; ITEM was appended to GuideMediaType.

Verification on 2026-10-14: 856 JUnit tests passed with no failures or skipped tests (`framework-fixes-final-second.log`, Forge 47.4.4 override). Independent acceptanceTest sources verify real Forge capability revival, native dialogue failure stopping, and full-creative-inventory dialogue/trade rejection and native trade rollback/retry. These three tests passed alongside the DuelQuest suite in `D:\DuelQuest\runtime-framework-final-second.log` (34 required tests total).

acceptanceTest is excluded from production JARs. It is loaded only in the opt-in DuelQuest smoke environment. Original user changes in CollectionFieldDemos remain outside these commits. End-to-end content completion and production deployment require their own evidence.
