import {renderConditionTree} from '../condition-editor.js';

function transitionTargetSelect(label, bind, value, phaseIds, selfId) {
    const options = ['<option value="">(未设置)</option>', ...phaseIds
        .filter(id => id && id !== selfId)
        .map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

export function renderPhaseChoicesSection(s, p, phaseIds, field, conditionOptions) {
    if (p.mode !== 'choice') return '';
    return `
    <h4>阶段分支选项 (Choices)</h4>
    <div class="card">
      ${(p.choices || []).map((c, ci) => `
        <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
          <div class="row">
            ${field('text', `ph.${s.pi}.ch.${ci}.text`, c.text || '')}
            ${transitionTargetSelect('targetPhaseId', `ph.${s.pi}.ch.${ci}.targetPhaseId`, c.targetPhaseId || '', phaseIds, p.id)}
          </div>
          <div class="row">
            ${field('flagToSet', `ph.${s.pi}.ch.${ci}.flagToSet`, c.flagToSet || '')}
          </div>
          ${renderConditionTree(`ph.${s.pi}.ch.${ci}.vc`, c.visibleCondition, conditionOptions)}
          <div class="actions"><button data-dch="${ci}" class="danger">删除 Choice</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addChoiceBtn">+ 添加 Choice</button></div>
    </div>
  `;
}
