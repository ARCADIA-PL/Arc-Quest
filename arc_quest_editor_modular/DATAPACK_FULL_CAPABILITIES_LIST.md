# Arc Quest 数据包全能力清单（仅列举）

## 1) Quest 顶层字段能力

- `id`
- `category`
- `displayName`
- `description`
- `iconTexture`
- `sortOrder`
- `repeatable`
- `mode`（`PROGRESSION` / `COLLECTION`）
- `collectionConfig`
- `initialPhaseId`
- `phases`
- `unlockConditions`
- `completionRewards`
- `flagsToSetOnAccept`
- `flagsToSetOnComplete`
- `chapterShopId`
- `chapterShopType`（`TRADE` / `GACHA`）
- `chapterShopPersistent`
- `completionPolicy`（`ALL` / `ANY` / `N_OF_M` / `SPECIFIC_PHASE`）
- `completionRequiredCount`
- `completionTargetPhaseId`
- `timeLimitType`（`REAL_SECONDS` / `GAME_DAY_TIME`）
- `timeLimitValue`
- `chapterStartSound`
- `chapterFailSound`
- `chapterCompleteSound`
- `visualConfig`

## 2) Phase 字段能力

- `phaseId`
- `displayName`
- `description`
- `story`
- `objectives`
- `transitions`
- `choices`
- `phaseRewards`
- `flagsToSetOnEnter`
- `flagsToSetOnComplete`
- `tradeShopId`
- `intelSceneId`
- `enterCondition`
- `autoEnterByCondition`
- `collectionEntryConfig`
- `visualConfig`
- `relatedMarks`
- `phaseStartSound`
- `phaseCompleteSound`

## 3) Objective 字段能力

- `type`
- `targetId`
- `requiredCount`
- `displayText`
- `hidden`
- `optional`
- `npcId`
- `itemTag`
- `x`
- `y`
- `z`
- `radius`
- `countMode`
- `countBase`
- `countPerLevel`
- `countMin`
- `countMax`
- `extraData`
- `relatedMarks`

## 4) ObjectiveType 枚举能力

- `KILL`
- `COLLECT`
- `TALK`
- `INTERACT`
- `REACH_LOCATION`
- `DELIVER`
- `CRAFT`
- `OFFER`
- `CUSTOM`

## 5) Transition/Choice 能力

### Transition
- `targetPhaseId`
- `condition`

### Choice
- `text`
- `flagToSet`
- `targetPhaseId`
- `visibleCondition`

## 6) Condition 能力

- `always`
- `flag_set`
- `flag_not_set`
- `quest_completed`
- `variable`
- `and`
- `or`
- `not`

### variable 条件字段
- `variable`
- `compareOp`（`EQUAL` / `NOT_EQUAL` / `GREATER` / `GREATER_OR_EQUAL` / `LESS` / `LESS_OR_EQUAL`）
- `value`

## 7) Reward 能力

- `item`
- `flag_set`
- `flag_clear`
- `command`
- `var_set`
- `var_add`
- `var_subtract`
- `var_multiply`

## 8) CollectionQuestConfig 能力

- `categories`
- `completionRules`
- `rewardNodes`
- `trackerPresentationMode`
- `collectionPresentationMode`
- `allowCategoryCollapse`
- `showCompletedEntries`
- `showProgressInTracker`

## 9) CollectionCategory 能力

- `categoryId`
- `displayName`
- `sortOrder`
- `completionRules`
- `rewardNodes`

## 10) CollectionRewardNode 能力

- `nodeId`
- `scope`（`QUEST` / `CATEGORY` / `PHASE`）
- `grantMode`（`AUTO` / `MANUAL`）
- `rewards`
- `completionRules`
- `scopeRefId`

## 11) CollectionEntryConfig 能力

- `categoryId`
- `visibilityMode`（`VISIBLE_BY_DEFAULT` / `HIDDEN_BY_DEFAULT` / `LOCKED`）
- `hiddenPresentationMode`（`FULLY_HIDDEN` / `PLACEHOLDER`）
- `visibilityConditions`
- `countingMode`（`BINARY` / `ACCUMULATE` / `UNIQUE_SET`）
- `completionTarget`
- `repeatableProgress`
- `repeatableCompletion`
- `maxCount`
- `rewardGrantMode`（`AUTO` / `MANUAL`）
- `rewardNodes`
- `sortOrder`
- `showInTrackerByDefault`

## 12) Collection Completion Rule 能力

- `all_entries_complete`
- `completed_entry_count`
- `category_completed_count`
- `completed_entry_ratio`
- `category_completed_ratio`
- `and`
- `or`
- `not`

## 13) VisualConfig 能力

- `themeColor`
- `splashes`
- `icons`

### splash 项能力
- `texture`
- `scale`

### icon 项能力
- `texture`
- `scale`

## 14) Mark 能力

- `relatedMarks`
- `target`
- `activateWhen`
- `deactivateWhen`
- `markerType`
- `priority`
- `maxDistance`
- `refreshTicks`
- `trackMovingEntity`
- `oneShot`
- `styleHints`
