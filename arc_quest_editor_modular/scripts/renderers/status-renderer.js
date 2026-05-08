import { buildReferenceIndex } from '../core/utils.js';

export function renderStatus(state, statusEl) {
  const errs = state.diag.filter(x => x.lvl === 'err').length;
  const warns = state.diag.filter(x => x.lvl === 'warn').length;
  const unresolvedRefs = buildReferenceIndex(state.q).unresolved.length;

  const errBadge = errs > 0 ? `<span style="color:var(--danger); font-weight:600;">Errors: ${errs}</span>` : `<span style="color:var(--success)">No Errors</span>`;
  const warnBadge = warns > 0 ? `<span style="color:var(--warning); font-weight:600;">Warns: ${warns}</span>` : ``;
  const refBadge = unresolvedRefs > 0 ? `<span style="color:var(--danger); font-weight:600;">BrokenRefs: ${unresolvedRefs}</span>` : `<span style="color:var(--text-mut)">BrokenRefs: 0</span>`;
  const dirtyBadge = state.meta.dirty ? `<span style="color:var(--warning)">● Unsaved</span>` : `<span style="color:var(--text-mut)">○ Synced</span>`;

  statusEl.innerHTML = `
    <div style="display:flex; gap:16px; align-items:center; flex:1;">
      <span style="color:var(--text-main); font-weight:600;">${state.meta.file}</span>
      <span style="opacity:0.3">|</span>
      ${dirtyBadge}
    </div>
    <div style="display:flex; gap:16px; align-items:center;">
      <span style="color:var(--text-mut)">Context: <span style="color:var(--accent); text-transform:uppercase">${state.ui.sel.t}</span></span>
      <span style="opacity:0.3">|</span>
      ${errBadge}
      ${warnBadge ? `<span style="opacity:0.3">|</span> ${warnBadge}` : ''}
      <span style="opacity:0.3">|</span>
      ${refBadge}
    </div>
  `;
}
