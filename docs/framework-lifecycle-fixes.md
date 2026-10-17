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

## Startup compatibility verification, 2026-10-17

Strict `GiveItem` ID validation exposed two old demo rewards containing potion SNBT inside an item ID. The elder and merchant now use a registered native custom action that constructs healing/strength potion stacks and passes them through atomic `InventoryDelivery`. Potion effects and declared quantities are retained; full creative inventories reject the reward.

The optional acceptance mod adds a native regression for both potion types, including full-inventory rejection and successful split-stack delivery. Forge 47.4.20 verification passed 890 JUnit tests and 53 required GameTests, including all nine framework acceptance cases. Evidence in the original workspace: `build/branch-sync-20261017/master-final-validation.log` and `master-server-acceptance.log`. The GameTest run uses `-PjeiServerAudit` and a temporary init script loading the acceptance source set into the test run. Release mapping checks and the standalone acceptance JAR also passed.

## NeoForge 1.21.1 alignment, 2026-10-17

The port keeps native NeoForge player attachments and their death-clone handling; Forge LazyOptional invalidation is specific to the source platform. The corresponding native regression checks attachment identity, snapshot copy ownership and cloned player progress. Item rewards and supplementary collection details retain Minecraft 1.21 data components. Supplementary details use registry-aware payload codecs and protocol version 23; quest kill credit implements NeoForge ICancellableEvent.

Verification: 898 JUnit tests, 45 existing required GameTests and nine optional framework required GameTests passed. `test build compileGameTestJava acceptanceTestJar`, the normal GameTest server (`-PjeiServerAudit`) and `runFrameworkAcceptanceServer -PframeworkAcceptanceAudit` succeeded with cached dependencies (`--offline`). Runtime actor fixtures have an outbound packet sink and no client handshake; wire codec coverage comes from the real registry-aware buffer round trips. Graphical client scenarios were not rerun in this synchronization.

Evidence: `build/framework-sync-validation-2.log`, `build/framework-sync-server-regressions.log`, `build/framework-sync-acceptance-2.log` and `build/framework-sync-release-final.log` in the 1.21.1 worktree. The production and sources JARs exclude acceptance classes and structures; `-acceptance-tests.jar` is a separate optional test mod.
