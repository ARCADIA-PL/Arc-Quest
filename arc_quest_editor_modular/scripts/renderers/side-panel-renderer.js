import { esc, clr } from '../core/utils.js';
import { renderGraph, bindGraphEvents, bindGraphShellEvents } from './graph-renderer.js';

export function renderSidePanel(state, tabsEl, rightEl, onTabChange, onNavigate, onGraphPhaseClick) {
  const ns = { graph: '拓扑', preview: '大纲', validate: '诊断', json: 'JSON', help: '指南' };
  tabsEl.innerHTML = Object.keys(ns).map(k => `<div class="tab ${state.ui.tab === k ? 'active' : ''}" data-t="${k}">${ns[k]}</div>`).join('');
  tabsEl.onclick = e => { const t = e.target.dataset.t; if (t) onTabChange(t); };

  let h = '';
  const wrap = content => `<div class="fade-in" style="height:100%; display:flex; flex-direction:column;">${content}</div>`;

  if (state.ui.tab === 'preview') h = wrap(`<div class="sec"><h3 style="color:var(--text-main); font-size:14px; font-weight:600; text-transform:none;">${esc(state.q.id)}</h3><div class="card"><div class="small" style="margin-bottom:8px;"><b>Title:</b> ${esc(state.q.title)}</div><div class="small" style="margin-bottom:8px;"><b>Theme:</b> <span class="chip" style="background:${esc(clr(state.q.visualConfig.themeColor))}20; color:${esc(clr(state.q.visualConfig.themeColor))}">${esc(clr(state.q.visualConfig.themeColor))}</span></div><div class="small" style="margin-bottom:8px;"><b>Scale:</b> ${state.q.phases.length} Phases / ${state.q.rewards.length} Rewards</div><div class="small"><b>Mode:</b> ${esc(state.q.mode || 'Standard')}</div></div></div>`);
  if (state.ui.tab === 'json') h = wrap(`<div class="json" style="flex:1; overflow:auto">${esc(JSON.stringify(state.q, null, 2))}</div>`);
  if (state.ui.tab === 'validate') {
    h = wrap(state.diag.length ? `<div class="sec"><h3 style="color:var(--danger)">Detected Issues (${state.diag.length})</h3>` + state.diag.map(d => `<div class="diag ${d.lvl}" data-path="${esc(d.path)}"><b style="display:block; margin-bottom:4px; font-size:11px;">[${d.lvl.toUpperCase()}]</b><div style="color:var(--text-main); font-weight:500">${esc(d.msg)}</div><div class="tiny" style="margin-top:6px; font-family:monospace">${esc(d.path)}</div></div>`).join('') + `</div>` : '<div class="sec"><div class="diag info" style="text-align:center; padding: 24px;"><svg viewBox="0 0 24 24" width="32" height="32" stroke="var(--success)" stroke-width="2" fill="none" style="margin-bottom:12px;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg><div style="font-size:14px; color:var(--success); font-weight:600">系统诊断通过</div><div class="small" style="margin-top:8px">未发现任何配置结构异常。</div></div></div>');
  }
  if (state.ui.tab === 'help') h = wrap('<div class="sec"><h3>Quick Guide</h3><div class="card" style="line-height:1.6"><b style="color:var(--text-main)">Pro Update:</b><br/>已采用全新流式界面引擎。<br/>- 支持沉浸式焦点编辑<br/>- 拓扑图支持智能点选联动<br/>- 诊断列表支持一键寻址定位<br/>- 全局支持亚克力磨砂透视。</div></div>');
  if (state.ui.tab === 'graph') h = wrap(renderGraph(state));

  rightEl.innerHTML = h;
  rightEl.querySelectorAll('[data-path]').forEach(el => el.onclick = () => onNavigate(el.dataset.path));

  if (state.ui.tab === 'graph') {
    const shell = rightEl.querySelector('.graph-shell');
    if (shell) {
      const compact = shell.querySelector('.graph-viewport-compact');
      if (compact) bindGraphEvents(compact, state, onGraphPhaseClick);
      bindGraphShellEvents(shell, state, onGraphPhaseClick);
    }
  }
}
