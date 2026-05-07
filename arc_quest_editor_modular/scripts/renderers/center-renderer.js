import { renderQuestEditor } from '../editors/quest-editor.js';
import { renderVisualEditor } from '../editors/visual-editor.js';
import { renderPhaseEditor } from '../editors/phase-editor.js';
import { renderObjectiveEditor } from '../editors/objective-editor.js';
import { renderRawEditor } from '../editors/raw-editor.js';
import { renderRewardList } from '../editors/reward-editor.js';
import { esc } from '../core/utils.js';

export function field(label, bind, value, type = 'text') {
  return `<div class="f"><label>${label}</label><input data-b="${bind}" type="${type}" value="${esc(value)}"></div>`;
}

export function area(label, bind, value) {
  return `<div class="f"><label>${label}</label><textarea data-b="${bind}">${esc(value)}</textarea></div>`;
}

export function renderCenterEditor(state, midEl) {
  const s = state.ui.sel;
  let html = '';
  if (s.t === 'quest') html = renderQuestEditor(state, field, area);
  else if (s.t === 'visual') html = renderVisualEditor(state, field);
  else if (s.t === 'rewards') html = `<div class="sec"><h3>Quest Rewards</h3>${renderRewardList(state.q.rewards, 'quest', null, field)}<div class="actions"><button id="addQuestRewardBtn">+ 新增 Reward</button></div></div>`;
  else if (s.t === 'phase') html = renderPhaseEditor(state, field, area);
  else if (s.t === 'obj') html = renderObjectiveEditor(state, field);
  else html = renderRawEditor(state, esc);
  midEl.innerHTML = html;
}
