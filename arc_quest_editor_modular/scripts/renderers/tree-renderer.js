import { esc } from '../core/utils.js';

export function renderTree(state, target) {
  const s = state.ui.sel;
  const q = state.q;
  const item = (id, label, active) => `<div class="tree ${active ? 'on' : ''}" data-id="${id}">${label}</div>`;
  let h = `<div class="sec"><h3>结构导航</h3>${item('quest','Quest 基础信息',s.t==='quest')}${item('visual','VisualConfig',s.t==='visual')}${item('rewards','Quest Rewards',s.t==='rewards')}<div class="tree">Phases</div><div class="sub">`;
  q.phases.forEach((p, pi) => {
    h += item(`phase-${pi}`, `${esc(p.id)} <span class="small">[${esc(p.mode || 'normal')}] obj:${p.objectives.length} rw:${p.rewards.length}</span>`, s.t === 'phase' && s.pi === pi);
    h += '<div class="sub">';
    p.objectives.forEach((o, oi) => { h += item(`obj-${pi}-${oi}`, `${oi + 1}. ${esc(o.type)} <span class="small">${esc(o.id)}</span>`, s.t === 'obj' && s.pi === pi && s.oi === oi); });
    h += '</div>';
  });
  target.innerHTML = h + `</div>${item('raw', 'Raw JSON', s.t === 'raw')}</div>`;
}
