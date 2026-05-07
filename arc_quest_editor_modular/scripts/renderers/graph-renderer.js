import { esc, clr, phaseModeColor } from '../core/utils.js';

function nodeStroke(state, phase, index, selected) {
  const bad = state.diag.some(d => d.path.startsWith(`phase:${index}`) || d.path.startsWith(`objective:${index}:`));
  if (bad) return '#ff6b6b';
  if (selected) return clr(state.q.visualConfig.themeColor);
  return phaseModeColor(phase.mode);
}

function branchLines(phases, nodes) {
  let lines = '';
  phases.forEach((p, i) => {
    if (i < phases.length - 1) {
      const a = nodes[i], b = nodes[i + 1];
      lines += `<line x1="${a.x + 72}" y1="${a.y}" x2="${b.x}" y2="${b.y}" stroke="#4a6b8f" stroke-width="2"/>`;
    }
    if (p.mode === 'parallel') {
      (p.parallelPhaseIds || []).forEach(targetId => {
        const ti = phases.findIndex(x => x.id === targetId);
        if (ti >= 0) {
          const a = nodes[i], b = nodes[ti];
          lines += `<line x1="${a.x + 72}" y1="${a.y + 16}" x2="${b.x}" y2="${b.y - 16}" stroke="var(--parallel)" stroke-width="2" stroke-dasharray="6 4"/>`;
        }
      });
    }
    if (p.mode === 'choice') {
      (p.choicePhaseIds || []).forEach(targetId => {
        const ti = phases.findIndex(x => x.id === targetId);
        if (ti >= 0) {
          const a = nodes[i], b = nodes[ti];
          lines += `<line x1="${a.x + 72}" y1="${a.y - 16}" x2="${b.x}" y2="${b.y + 16}" stroke="var(--choice)" stroke-width="2" stroke-dasharray="3 4"/>`;
        }
      });
    }
  });
  return lines;
}

export function renderGraph(state) {
  const q = state.q;
  const gap = 112;
  const nodes = q.phases.map((p, i) => ({ p, i, x: 52 + i * gap, y: 118, sel: state.ui.sel.pi === i && (state.ui.sel.t === 'phase' || state.ui.sel.t === 'obj') }));
  let cards = '';
  nodes.forEach(x => {
    const stroke = nodeStroke(state, x.p, x.i, x.sel);
    const fill = x.p.mode === 'parallel' ? '#211b33' : x.p.mode === 'choice' ? '#2d2416' : '#16202b';
    cards += `<g class="graph-node" data-phase-index="${x.i}"><rect x="${x.x}" y="${x.y - 28}" width="78" height="60" rx="12" fill="${fill}" stroke="${stroke}" stroke-width="2"/><text x="${x.x + 39}" y="${x.y - 8}" fill="#e8edf7" font-size="11" text-anchor="middle">${esc(x.p.id)}</text><text x="${x.x + 39}" y="${x.y + 8}" fill="#91a0b8" font-size="10" text-anchor="middle">${esc(x.p.mode || 'normal')}</text><text x="${x.x + 39}" y="${x.y + 22}" fill="#91a0b8" font-size="10" text-anchor="middle">obj:${x.p.objectives.length} rw:${x.p.rewards.length}</text></g>`;
  });
  const lines = branchLines(q.phases, nodes);
  const sel = state.ui.sel.t === 'phase' || state.ui.sel.t === 'obj' ? q.phases[state.ui.sel.pi] : null;
  return `<div class="graph"><div class="small" style="margin-bottom:10px">QUEST // FLOW PREVIEW</div><svg viewBox="0 0 ${Math.max(420, 80 + q.phases.length * gap)} 260"><text x="8" y="122" fill="#91a0b8" font-size="11">Start</text>${lines}${cards}</svg><div class="small" style="margin-top:8px">当前选中：${sel ? esc(sel.id) : '-'}</div>${sel ? `<div class="tiny">mode: ${esc(sel.mode || 'normal')} | objectives: ${sel.objectives.length} | rewards: ${sel.rewards.length}</div>` : ''}<div class="legend small"><span><span class="dot" style="background:#3b465a"></span>主流程</span><span><span class="dot" style="background:${clr(q.visualConfig.themeColor)}"></span>当前编辑</span><span><span class="dot" style="background:#ff6b6b"></span>有错误</span><span><span class="dot" style="background:var(--parallel)"></span>parallel 分支</span><span><span class="dot" style="background:var(--choice)"></span>choice 分支</span></div></div>`;
}
