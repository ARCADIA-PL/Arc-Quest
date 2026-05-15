import {buildReferenceIndex} from '../core/utils.js';

export function renderStatus(state, statusEl) {
    if (state.mode === 'npc') {
        renderNpcStatus(state, statusEl);
        return;
    }
    if (state.mode === 'dialogue') {
        renderDialogueStatus(state, statusEl);
        return;
    }

    const errs = state.quest.diag.filter(x => x.lvl === 'err').length;
    const warns = state.quest.diag.filter(x => x.lvl === 'warn').length;
    const unresolvedRefs = buildReferenceIndex(state.quest.q).unresolved.length;

    const errBadge = errs > 0 ? `<span style="color:var(--danger); font-weight:600;">Errors: ${errs}</span>` : `<span style="color:var(--success)">No Errors</span>`;
    const warnBadge = warns > 0 ? `<span style="color:var(--warning); font-weight:600;">Warns: ${warns}</span>` : ``;
    const refBadge = unresolvedRefs > 0 ? `<span style="color:var(--danger); font-weight:600;">BrokenRefs: ${unresolvedRefs}</span>` : `<span style="color:var(--text-mut)">BrokenRefs: 0</span>`;
    const dirtyBadge = state.quest.meta.dirty ? `<span style="color:var(--warning)">● Unsaved</span>` : `<span style="color:var(--text-mut)">○ Synced</span>`;

    statusEl.innerHTML = `
    <div style="display:flex; gap:16px; align-items:center; flex:1;">
      <span style="color:var(--text-main); font-weight:600;">${state.quest.meta.file}</span>
      <span style="opacity:0.3">|</span>
      ${dirtyBadge}
    </div>
    <div style="display:flex; gap:16px; align-items:center;">
      <span style="color:var(--text-mut)">Context: <span style="color:var(--accent); text-transform:uppercase">${state.quest.ui.sel.t}</span></span>
      <span style="opacity:0.3">|</span>
      ${errBadge}
      ${warnBadge ? `<span style="opacity:0.3">|</span> ${warnBadge}` : ''}
      <span style="opacity:0.3">|</span>
      ${refBadge}
    </div>
  `;
}

function renderNpcStatus(state, statusEl) {
    const errs = state.npc.diag.filter(x => x.lvl === 'err').length;
    const warns = state.npc.diag.filter(x => x.lvl === 'warn').length;
    const errBadge = errs > 0 ? `<span style="color:var(--danger); font-weight:600;">Errors: ${errs}</span>` : `<span style="color:var(--success)">No Errors</span>`;
    const warnBadge = warns > 0 ? `<span style="color:var(--warning); font-weight:600;">Warns: ${warns}</span>` : '';
    const dirtyBadge = state.npc.meta.dirty ? `<span style="color:var(--warning)">● Unsaved</span>` : `<span style="color:var(--text-mut)">○ Synced</span>`;

    statusEl.innerHTML = `
    <div style="display:flex; gap:16px; align-items:center; flex:1;">
      <span style="color:var(--text-main); font-weight:600;">${state.npc.meta.file}</span>
      <span style="opacity:0.3">|</span>
      <span style="color:var(--accent)">${state.npc.q.entityType || '<em>未设置实体</em>'}</span>
      <span style="opacity:0.3">|</span>
      ${dirtyBadge}
    </div>
    <div style="display:flex; gap:16px; align-items:center;">
      <span style="color:var(--text-mut)">NPC Mode</span>
      <span style="opacity:0.3">|</span>
      ${errBadge}
      ${warnBadge ? `<span style="opacity:0.3">|</span> ${warnBadge}` : ''}
    </div>
  `;
}

function renderDialogueStatus(state, statusEl) {
    const errs = state.dialogue.diag.filter(x => x.lvl === 'err').length;
    const warns = state.dialogue.diag.filter(x => x.lvl === 'warn').length;
    const errBadge = errs > 0 ? `<span style="color:var(--danger); font-weight:600;">Errors: ${errs}</span>` : `<span style="color:var(--success)">No Errors</span>`;
    const warnBadge = warns > 0 ? `<span style="color:var(--warning); font-weight:600;">Warns: ${warns}</span>` : '';
    const dirtyBadge = state.dialogue.meta.dirty ? `<span style="color:var(--warning)">● Unsaved</span>` : `<span style="color:var(--text-mut)">○ Synced</span>`;

    statusEl.innerHTML = `
    <div style="display:flex; gap:16px; align-items:center; flex:1;">
      <span style="color:var(--text-main); font-weight:600;">${state.dialogue.meta.file}</span>
      <span style="opacity:0.3">|</span>
      <span style="color:var(--accent)">${state.dialogue.q.id || '<em>未设置 ID</em>'}</span>
      <span style="opacity:0.3">|</span>
      ${dirtyBadge}
    </div>
    <div style="display:flex; gap:16px; align-items:center;">
      <span style="color:var(--text-mut)">Dialogue Mode · ${state.dialogue.q.nodes.length} nodes</span>
      <span style="opacity:0.3">|</span>
      ${errBadge}
      ${warnBadge ? `<span style="opacity:0.3">|</span> ${warnBadge}` : ''}
    </div>
  `;
}
