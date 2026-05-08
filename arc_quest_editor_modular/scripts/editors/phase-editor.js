import { renderRewardList } from './reward-editor.js';
import { renderPhaseModeSummary } from './shared.js';

function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

function multiPhaseSelect(label, bind, selectedIds, phaseIds, selfId) {
  const selected = new Set(selectedIds || []);
  const options = phaseIds
    .filter(id => id && id !== selfId)
    .map(id => `<option value="${id}" ${selected.has(id) ? 'selected' : ''}>${id}</option>`)
    .join('');
  return `<div class="f"><label>${label}</label><select data-b="${bind}" multiple size="5">${options}</select></div>`;
}

function transitionTargetSelect(label, bind, value, phaseIds, selfId) {
  const options = ['<option value="">(未设置)</option>', ...phaseIds
    .filter(id => id && id !== selfId)
    .map(id => `<option value="${id}" ${value === id ? 'selected' : ''}>${id}</option>`)];
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.join('')}</select></div>`;
}

export function renderPhaseEditor(state, field, area) {
  const s = state.ui.sel;
  const p = state.q.phases[s.pi];
  const phaseIds = (state.q.phases || []).map(x => x.id).filter(Boolean);

  return `
    <div class="sec">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;">
        <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin:0;">阶段节点配置 (Phase: ${s.pi})</h3>
        ${renderPhaseModeSummary(p)}
      </div>

      <div class="card">
        ${field('阶段标识 (Phase ID)', `ph.${s.pi}.id`, p.id)}
        <div class="row">
          ${field('标题 (Title)', `ph.${s.pi}.title`, p.title || '')}
          ${modeSelect('Title Mode', `ph.${s.pi}.titleMode`, p.titleMode || 'translatable')}
        </div>
        <div class="row">
          ${area('描述 (Description)', `ph.${s.pi}.description`, p.description || '')}
          ${modeSelect('Description Mode', `ph.${s.pi}.descriptionMode`, p.descriptionMode || 'translatable')}
        </div>
      </div>

      <h4>流程与执行策略 (Phase Flow)</h4>
      <div class="card">
        <div class="row">
          <div class="f"><label>运行模式 (Mode)</label><select data-b="ph.${s.pi}.mode"><option value="normal" ${p.mode === 'normal' ? 'selected' : ''}>Normal (常规)</option><option value="parallel" ${p.mode === 'parallel' ? 'selected' : ''}>Parallel (平行)</option><option value="choice" ${p.mode === 'choice' ? 'selected' : ''}>Choice (选择)</option></select></div>
          <div class="f"><label>自动启动 (Auto Start)</label><select data-b="ph.${s.pi}.autoStart"><option value="false" ${!p.autoStart ? 'selected' : ''}>False</option><option value="true" ${p.autoStart ? 'selected' : ''}>True</option></select></div>
        </div>
        <div class="row">
          ${multiPhaseSelect('平行阶段关联 (parallelPhaseIds)', `ph.${s.pi}.parallelPhaseIds`, p.parallelPhaseIds || [], phaseIds, p.id)}
          ${multiPhaseSelect('选择阶段关联 (choicePhaseIds)', `ph.${s.pi}.choicePhaseIds`, p.choicePhaseIds || [], phaseIds, p.id)}
        </div>
        <div class="small">提示：按住 Ctrl / Cmd 可多选，当前 phase 不会出现在候选列表中。</div>
      </div>

      <h4>过渡连接 (Transitions)</h4>
      <div class="card">
        ${(p.transitions || []).map((t, ti) => `
          <div class="card" style="margin:8px 0; border-color: rgba(255,255,255,0.1);">
            <div class="row">
              ${transitionTargetSelect('目标阶段 (targetPhaseId)', `ph.${s.pi}.tr.${ti}.targetPhaseId`, t.targetPhaseId || '', phaseIds, p.id)}
              <div class="f"><label>条件类型</label><select data-b="ph.${s.pi}.tr.${ti}.conditionType"><option value="always" ${(t.condition?.type || 'always') === 'always' ? 'selected' : ''}>always</option><option value="flag_set" ${t.condition?.type === 'flag_set' ? 'selected' : ''}>flag_set</option></select></div>
            </div>
            ${t.condition?.type === 'flag_set' ? field('Flag', `ph.${s.pi}.tr.${ti}.c.flag`, t.condition?.flag || '') : ''}
            <div class="actions"><button data-dt="${ti}" class="danger">删除过渡</button></div>
          </div>
        `).join('')}
        <div class="actions"><button id="addTransitionBtn">+ 添加过渡</button></div>
      </div>

      <h4>阶段目标 (Objectives)</h4>
      <div class="card" style="background: rgba(0,0,0,0.2)">
        ${(p.objectives || []).map((o, oi) => `
          <div class="card" style="margin: 8px 0; border-color: rgba(255,255,255,0.1);">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div>
                <b style="color:var(--accent); font-size:14px;">${oi + 1}. ${o.type}</b>
                <div class="small" style="margin-top:4px; font-family:monospace">${o.id || ''}</div>
              </div>
              <div class="actions" style="margin-top:0;">
                <button data-open="${oi}">编辑配置</button>
                <button data-do="${oi}" class="danger">移除</button>
              </div>
            </div>
          </div>
        `).join('')}
        <div class="actions"><button id="addObjectiveBtn" class="primary">+ 添加新目标 (Objective)</button></div>
      </div>

      <h4>阶段奖励 (Phase Rewards)</h4>
      <div class="card">
        ${renderRewardList(p.rewards, 'phase', s.pi, field)}
        <div class="actions"><button id="addPhaseRewardBtn">+ 添加奖励</button></div>
      </div>

      <div class="actions" style="margin-top:40px; padding-top:20px; border-top:1px dashed var(--card-border);">
        <button id="movePhaseUpBtn">↑ 上移节点</button>
        <button id="movePhaseDownBtn">↓ 下移节点</button>
        <button id="deletePhaseBtn" class="danger" style="margin-left:auto;">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg> 删除此阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}
