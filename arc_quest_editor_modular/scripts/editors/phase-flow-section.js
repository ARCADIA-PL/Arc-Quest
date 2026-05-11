import {renderConditionTree} from './condition-editor.js';
import {chipEditor} from './chip-editor.js';

export function renderPhaseFlowSection(s, p, phaseIds, field, area, conditionOptions) {
    const isParallel = p.mode === 'parallel';
    const isChoice = p.mode === 'choice';
    const hasEnterCondition = !!p.rawEnterCondition;
    const transitionTargetOptions = ['<option value="">(未设置)</option>', ...phaseIds
        .filter(id => id && id !== p.id)
        .map(id => `<option value="${id}" ${p.targetPhaseId === id ? 'selected' : ''}>${id}</option>`)].join('');

    return `
    <h4>流程与执行策略 (Phase Flow)</h4>
    <div class="card">
      <div class="row">
        <div class="f"><label>运行模式 (Mode)</label><select data-b="ph.${s.pi}.mode"><option value="normal" ${p.mode === 'normal' ? 'selected' : ''}>Normal (常规)</option><option value="parallel" ${isParallel ? 'selected' : ''}>Parallel (平行)</option><option value="choice" ${isChoice ? 'selected' : ''}>Choice (选择)</option></select></div>
        <div class="f"><label>启用 enterWhen</label><select data-b="ph.${s.pi}.hasEnterCondition"><option value="false" ${!hasEnterCondition ? 'selected' : ''}>False</option><option value="true" ${hasEnterCondition ? 'selected' : ''}>True</option></select></div>
      </div>
      ${hasEnterCondition ? `
      <div class="row">
        <div class="f"><label>满足条件时是否自动进入阶段 (autoEnterByCondition)</label><select data-b="ph.${s.pi}.autoStart"><option value="false" ${!p.autoStart ? 'selected' : ''}>False</option><option value="true" ${p.autoStart ? 'selected' : ''}>True</option></select></div>
        <div class="f"></div>
      </div>
      <div class="card" style="margin-top:8px; border-color: rgba(255,255,255,0.08)">
        <div class="small"><b>进入条件 (enterCondition)</b></div>
        ${renderConditionTree(`ph.${s.pi}.ec`, p.rawEnterCondition, conditionOptions)}
      </div>
      ` : '<div class="small">当前未启用 enterWhen，因此不显示进入条件 (enterCondition) 和 autoEnterByCondition。</div>'}
      <div class="row">
        <div class="f"><label>完成后自动推进 (autoAdvanceOnComplete)</label><select data-b="ph.${s.pi}.autoAdvanceOnComplete"><option value="true" ${(p.autoAdvanceOnComplete ?? true) ? 'selected' : ''}>True</option><option value="false" ${!(p.autoAdvanceOnComplete ?? true) ? 'selected' : ''}>False</option></select></div>
        ${field('intelSceneId', `ph.${s.pi}.intelSceneId`, p.intelSceneId || '')}
      </div>
      <div class="row">
        ${field('tradeShopId', `ph.${s.pi}.tradeShopId`, p.tradeShopId || '')}
        <div class="f"></div>
      </div>
      <div class="row">
        ${field('phaseStartSound', `ph.${s.pi}.phaseStartSound`, p.phaseStartSound || '')}
        ${field('phaseCompleteSound', `ph.${s.pi}.phaseCompleteSound`, p.phaseCompleteSound || '')}
      </div>
      <div class="row">
        ${chipEditor('接取时触发标记 (setFlagOnEnter)', p.flagsToSetOnEnter || [], `ph.${s.pi}.flagsToSetOnEnter`, `ph.${s.pi}.flagsToSetOnEnter`, '输入 flag 后点击添加')}
        ${chipEditor('完成时触发标记 (setFlagOnComplete)', p.flagsToSetOnComplete || [], `ph.${s.pi}.flagsToSetOnComplete`, `ph.${s.pi}.flagsToSetOnComplete`, '输入 flag 后点击添加')}
      </div>
      <div class="small">${isParallel ? '并行阶段请直接在下方“过渡连接”中维护多个目标阶段。' : isChoice ? 'Choice 阶段请直接在下方“阶段分支选项”中维护分支文本与目标。' : '当前为普通阶段，不显示并行/分支子配置。'}</div>
    </div>

    <h4>Phase 附加结构</h4>
    <div class="card">
      ${area('relatedMarks JSON', `ph.${s.pi}.relatedMarks`, p.relatedMarks ? JSON.stringify(p.relatedMarks, null, 2) : '')}
      ${area('visualConfig JSON', `ph.${s.pi}.visualConfig`, p.visualConfig ? JSON.stringify(p.visualConfig, null, 2) : '')}
    </div>
  `;
}
