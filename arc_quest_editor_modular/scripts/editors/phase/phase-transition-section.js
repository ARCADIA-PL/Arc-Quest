import {renderConditionTree} from '../condition-editor.js';

function transitionTargetSelect(label, bind, value, phaseIds, selfId) {
    const options = ['<option value="">(未设置)</option>', ...phaseIds
        .filter(id => id && id !== selfId)
        .map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

export function renderPhaseTransitionsSection(s, p, phaseIds, conditionOptions) {
    const title = p.mode === 'choice' ? '阶段去向 (Choice Targets / Transitions)' : '过渡连接 (Transitions)';
    return `
    <h4>${title}</h4>
    <div class="card">
      ${(p.transitions || []).map((t, ti) => `
        <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
          <div class="row">
            ${transitionTargetSelect('目标阶段 (targetPhaseId)', `ph.${s.pi}.tr.${ti}.targetPhaseId`, t.targetPhaseId || '', phaseIds, p.id)}
          </div>
          ${renderConditionTree(`ph.${s.pi}.tr.${ti}.c`, t.condition, conditionOptions)}
          <div class="actions"><button data-dt="${ti}" class="danger">删除过渡</button></div>
        </div>
      `).join('')}
      <div class="actions"><button id="addTransitionBtn">+ 添加过渡</button></div>
    </div>
  `;
}
