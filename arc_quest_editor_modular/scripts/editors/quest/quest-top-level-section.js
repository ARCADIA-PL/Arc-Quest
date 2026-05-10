import { renderConditionTree } from '../condition-editor.js';
import { chipEditor } from '../chip-editor.js';
import { boolSelect, enumSelect, phaseSingleSelect, suggestInput } from '../quest-editor-sections.js';

function renderUnlockConditionsSection(q, conditionOptions) {
  const conditions = Array.isArray(q.unlockConditions) ? q.unlockConditions : [];
  return `
    <h4>解锁条件 (unlockConditions)</h4>
    <div class="card">
      ${conditions.length ? conditions.map((condition, index) => `
        <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1)">
          <div class="small"><b>Condition ${index + 1}</b></div>
          ${renderConditionTree(`q.uc.${index}`, condition, conditionOptions)}
          <div class="actions"><button data-duc="${index}" class="danger">删除条件</button></div>
        </div>
      `).join('') : '<div class="tiny">暂无解锁条件</div>'}
      <div class="actions"><button id="addUnlockConditionBtn">+ 添加解锁条件</button></div>
    </div>
  `;
}

export function renderQuestTopLevelSection(q, phaseIds, field, area) {
  const isCollectionQuest = q.mode === 'COLLECTION';
  const isTimed = !!q.timeLimitType && Number(q.timeLimitValue || 0) > 0;
  const requiresCount = q.completionPolicy === 'N_OF_M';
  const requiresTargetPhase = q.completionPolicy === 'SPECIFIC_PHASE';
  const flagSuggestions = Array.from(new Set([
    ...(q.flagsToSetOnAccept || []),
    ...(q.flagsToSetOnComplete || []),
    ...(q.phases || []).flatMap(phase => [
      ...(phase.flagsToSetOnEnter || []),
      ...(phase.flagsToSetOnComplete || []),
      ...(phase.choices || []).map(choice => choice.flagToSet).filter(Boolean)
    ])
  ].filter(Boolean)));
  const questSuggestions = Array.from(new Set([q.id, ...(q.phases || []).map(phase => phase.id).filter(Boolean)])).filter(Boolean);
  const conditionOptions = { flagSuggestions, questSuggestions };
  return `
    <h4>Datapack 顶层设置 (Top-level Specs)</h4>
    <div class="card">
      <div class="row">
        ${field('分类 (Category)', 'q.category', q.category || '')}
        ${enumSelect('模式 (Mode)', 'q.mode', q.mode || 'PROGRESSION', ['PROGRESSION', 'COLLECTION'])}
      </div>
      ${isCollectionQuest ? `
      <div class="row">
        ${enumSelect('完成策略 (completionPolicy)', 'q.completionPolicy', q.completionPolicy || 'ALL', ['ALL', 'ANY', 'N_OF_M', 'SPECIFIC_PHASE'])}
        ${requiresCount ? field('完成数量阈值 (completionRequiredCount)', 'q.completionRequiredCount', q.completionRequiredCount ?? 1, 'number') : '<div class="f"><label>完成数量阈值</label><div class="tiny">仅 N_OF_M 模式需要</div></div>'}
      </div>
      <div class="row">
        ${requiresTargetPhase ? phaseSingleSelect('完成目标阶段 (completionTargetPhaseId)', 'q.completionTargetPhaseId', q.completionTargetPhaseId || '', phaseIds) : '<div class="f"><label>完成目标阶段</label><div class="tiny">仅 SPECIFIC_PHASE 模式需要</div></div>'}
        <div class="f"></div>
      </div>
      ` : ''}
      <div class="row">
        ${boolSelect('是否限时', 'q.hasTimeLimit', isTimed)}
        ${phaseSingleSelect('初始阶段ID (Initial Phase ID)', 'q.initialPhaseId', q.initialPhaseId || '', phaseIds)}
      </div>
      ${isTimed ? `
      <div class="row">
        ${enumSelect('时限类型 (timeLimitType)', 'q.timeLimitType', q.timeLimitType || 'REAL_SECONDS', ['REAL_SECONDS', 'GAME_DAY_TIME'])}
        ${field('时限值 (timeLimitValue)', 'q.timeLimitValue', q.timeLimitValue ?? 0, 'number')}
      </div>
      ` : ''}
      <div class="row">
        ${field('商店ID (Chapter Shop ID)', 'q.chapterShopId', q.chapterShopId || '')}
        ${enumSelect('商店类型 (Chapter Shop Type)', 'q.chapterShopType', q.chapterShopType || 'TRADE', ['TRADE', 'GACHA'])}
      </div>
      <div class="row">
        ${suggestInput('图标路径 (Icon Texture)', 'q.iconTexture', q.iconTexture || '', ['minecraft:textures/item/iron_ingot.png', 'minecraft:textures/item/diamond.png', 'arc_quest:textures/gui/quest.png'], 'iconTextureSuggest', 'namespace:path/to/texture.png')}
        ${field('章节开始音效 (chapterStartSound)', 'q.chapterStartSound', q.chapterStartSound || '')}
      </div>
      <div class="row">
        ${field('章节失败音效 (chapterFailSound)', 'q.chapterFailSound', q.chapterFailSound || '')}
        ${field('章节完成音效 (chapterCompleteSound)', 'q.chapterCompleteSound', q.chapterCompleteSound || '')}
      </div>
      <div class="row">
        ${boolSelect('商店常驻 (Persistent)', 'q.chapterShopPersistent', q.chapterShopPersistent)}
        ${chipEditor('完成时触发标记 (setFlagOnComplete)', q.flagsToSetOnComplete || [], 'q.flagsToSetOnComplete', 'q.flagsToSetOnComplete', '输入 flag 后点击添加')}
      </div>
      <div class="row">
        <div class="f">${renderUnlockConditionsSection(q, conditionOptions)}</div>
        ${area('相关标记 (relatedMarks JSON)', 'q.relatedMarks', q.relatedMarks ? JSON.stringify(q.relatedMarks, null, 2) : '')}
      </div>
    </div>
  `;
}
