import { esc } from '../core/utils.js';

export function renderTree(state, target) {
  const s = state.ui.sel;
  const q = state.q;

  // Icon helpers
  const icFolder = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>`;
  const icFile = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>`;
  const icTarget = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle></svg>`;

  const item = (id, label, active, icon='') => `<div class="tree ${active ? 'on' : ''}" data-id="${id}">${icon}<span style="flex:1; margin-left:8px;">${label}</span></div>`;

  let h = `<div class="sec">
    <h3>Configuration</h3>
    ${item('quest', '架构主参 (Quest)', s.t === 'quest', icFolder)}
    ${item('visual', '视觉配置 (Visual)', s.t === 'visual', icFolder)}
    ${item('rewards', '全局奖励 (Rewards)', s.t === 'rewards', icFolder)}
    
    <h3 style="margin-top: 24px;">Phases Stream</h3>
    <div class="sub" style="margin-left:0; border:none; padding:0;">`;

  q.phases.forEach((p, pi) => {
    // 动态标签颜色
    let badgeClass = 'phase-normal';
    if(p.mode === 'parallel') badgeClass = 'phase-parallel';
    if(p.mode === 'choice') badgeClass = 'phase-choice';

    h += item(`phase-${pi}`, `${esc(p.id)} <div style="display:flex; gap:4px; margin-top:2px"><span class="chip ${badgeClass}">${(p.mode || 'normal').substring(0,3).toUpperCase()}</span><span class="chip" style="color:var(--text-mut)">O:${p.objectives.length} R:${p.rewards.length}</span></div>`, s.t === 'phase' && s.pi === pi, icFile);

    if (p.objectives.length > 0) {
      h += '<div class="sub" style="margin-bottom:8px;">';
      p.objectives.forEach((o, oi) => {
        h += item(`obj-${pi}-${oi}`, `<span style="font-size:12px">${esc(o.type)}</span>`, s.t === 'obj' && s.pi === pi && s.oi === oi, icTarget);
      });
      h += '</div>';
    }
  });

  target.innerHTML = h + `</div>
    <h3 style="margin-top: 24px;">System</h3>
    ${item('raw', '底层数据 (Raw JSON)', s.t === 'raw', `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>`)}
  </div>`;
}