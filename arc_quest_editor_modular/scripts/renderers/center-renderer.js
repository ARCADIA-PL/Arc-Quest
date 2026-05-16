import {renderQuestEditor} from '../editors/quest-editor.js';
import {renderVisualEditor} from '../editors/visual-editor.js';
import {renderPhaseEditor} from '../editors/phase-editor.js';
import {renderObjectiveEditor} from '../editors/objective-editor.js';
import {renderRawEditor} from '../editors/raw-editor.js';
import {renderNpcOverview} from '../editors/npc-editor.js';
import {renderNpcBindingEditor} from '../editors/npc-binding-editor.js';
import {renderDialogueCenter} from '../editors/dialogue-editor.js';
import {esc} from '../core/utils.js';
import {field, area} from './center/form-fields.js';
import {renderRewardsView} from './center/rewards-view.js';

export function renderCenterEditor(state, midEl) {
    const s = state.quest.ui.sel;
    let html = '';
    const wrap = content => `<div class="center-content">${content}</div>`;

    if (s.t === 'quest') html = wrap(renderQuestEditor(state, field, area));
    else if (s.t === 'visual') html = wrap(renderVisualEditor(state, field, area));
    else if (s.t === 'rewards') html = wrap(renderRewardsView(state, field));
    else if (s.t === 'phase') html = wrap(renderPhaseEditor(state, field, area));
    else if (s.t === 'obj') html = wrap(renderObjectiveEditor(state, field, area));
    else html = wrap(renderRawEditor(state, esc));

    midEl.innerHTML = html;
}

export function renderNpcCenter(state, midEl) {
    const sel = state.npc.ui.sel;
    const npc = state.npc.q;
    let html = '';
    if (sel.t === 'binding' && typeof sel.bi === 'number') {
        html = renderNpcBindingEditor(npc, sel.bi, state.registry);
    } else if (sel.t === 'commands') {
        html = renderNpcCommandsEditor(npc);
    } else {
        html = renderNpcOverview(npc, state.registry, state);
    }
    midEl.innerHTML = `<div class="center-content">${html}</div>`;
}

function renderNpcCommandsEditor(npc) {
    const startCmds = (npc.onDialogueStartCommands || []).join('\n');
    const endCmds = (npc.onDialogueEndCommands || []).join('\n');
    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-npc-nav="overview" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 返回</button>
        <span class="breadcrumb">
          <span data-npc-nav="overview" class="breadcrumb-link">⚙ NPC 配置</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">命令</span>
        </span>
      </div>
      <h3>对话开始命令</h3>
      <div class="f">
        <textarea data-b="npc.onDialogueStartCommands" data-b-array="true" rows="5" placeholder="每行一条命令">${startCmds}</textarea>
      </div>
      <h3 style="margin-top:12px">对话结束命令</h3>
      <div class="f">
        <textarea data-b="npc.onDialogueEndCommands" data-b-array="true" rows="5" placeholder="每行一条命令">${endCmds}</textarea>
      </div>
    </div>
  `;
}

export function renderDiagCenter(state, midEl) {
    const html = renderDialogueCenter(state);
    midEl.innerHTML = `<div class="center-content">${html}</div>`;
}
