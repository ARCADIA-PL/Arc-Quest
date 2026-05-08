import { renderQuestEditor } from '../editors/quest-editor.js';
import { renderVisualEditor } from '../editors/visual-editor.js';
import { renderPhaseEditor } from '../editors/phase-editor.js';
import { renderObjectiveEditor } from '../editors/objective-editor.js';
import { renderRawEditor } from '../editors/raw-editor.js';
import { renderRewardList } from '../editors/reward-editor.js';
import { esc } from '../core/utils.js';

export function field(label, bind, value, type = 'text') {
  return `<div class="f"><label>${label}</label><input data-b="${bind}" type="${type}" value="${esc(value)}" placeholder="${label}"></div>`;
}

export function area(label, bind, value) {
  return `<div class="f"><label>${label}</label><textarea data-b="${bind}" placeholder="${label}">${esc(value)}</textarea></div>`;
}

export function renderCenterEditor(state, midEl) {
  const s = state.ui.sel;
  let html = '';
  // 加入统一的外层淡入动画容器
  const wrap = (content) => `<div class="fade-in">${content}</div>`;

  if (s.t === 'quest') html = wrap(renderQuestEditor(state, field, area));
  else if (s.t === 'visual') html = wrap(renderVisualEditor(state, field, area));
  else if (s.t === 'rewards') html = wrap(`<div class="sec"><h3>Global Rewards</h3><div class="card">${renderRewardList(state.q.rewards, 'quest', null, field)}<div class="actions"><button class="primary" id="addQuestRewardBtn">+ 添加全局奖励</button></div></div></div>`);
  else if (s.t === 'phase') html = wrap(renderPhaseEditor(state, field, area));
  else if (s.t === 'obj') html = wrap(renderObjectiveEditor(state, field, area));
  else html = wrap(renderRawEditor(state, esc));

  midEl.innerHTML = html;
}