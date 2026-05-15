import {renderQuestEditor} from '../editors/quest-editor.js';
import {renderVisualEditor} from '../editors/visual-editor.js';
import {renderPhaseEditor} from '../editors/phase-editor.js';
import {renderObjectiveEditor} from '../editors/objective-editor.js';
import {renderRawEditor} from '../editors/raw-editor.js';
import {renderNpcEditor} from '../editors/npc-editor.js';
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
    const html = renderNpcEditor(state.npc.q, state.registry, field, area);
    midEl.innerHTML = `<div class="center-content">${html}</div>`;
}

export function renderDiagCenter(state, midEl) {
    const html = renderDialogueCenter(state);
    midEl.innerHTML = `<div class="center-content">${html}</div>`;
}
