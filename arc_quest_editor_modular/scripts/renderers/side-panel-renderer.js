import { esc, clr } from '../core/utils.js';
import { renderGraph } from './graph-renderer.js';

function renderCollectionPreview(state) {
  const cfg = state.q.collectionConfig;
  if (!cfg) return '';
  return `
    <div class="sec">
      <h3>Collection 摘要</h3>
      <div class="small">categories: ${(cfg.categories || []).length}</div>
      <div class="small">quest rewardNodes: ${(cfg.rewardNodes || []).length}</div>
      <div class="small">completionRules: ${(cfg.completionRules || []).length}</div>
      <div class="small">trackerPresentationMode: ${esc(cfg.trackerPresentationMode || '-')}</div>
      <div class="small">collectionPresentationMode: ${esc(cfg.collectionPresentationMode || '-')}</div>
      ${(cfg.categories || []).map(cat => `<div class="card"><b>${esc(cat.categoryId || 'category')}</b><div class="small">rewardNodes: ${(cat.rewardNodes || []).length}</div><div class="small">completionRules: ${(cat.completionRules || []).length}</div></div>`).join('')}
    </div>
  `;
}

export function renderSidePanel(state, tabsEl, rightEl, onTabChange, onNavigate, onGraphPhaseClick) {
  const ns = { preview: '预览', json: 'JSON', validate: '校验', help: '帮助', graph: '节点图' };
  tabsEl.innerHTML = Object.keys(ns).map(k => `<div class="tab ${state.ui.tab === k ? 'active' : ''}" data-t="${k}">${ns[k]}</div>`).join('');
  tabsEl.onclick = e => { const t = e.target.dataset.t; if (t) onTabChange(t); };
  let h = '';
  if (state.ui.tab === 'preview') h = `<div class="sec"><h3>${esc(state.q.id)}</h3><div class="small">${esc(state.q.title)}</div><div class="small">themeColor: ${esc(clr(state.q.visualConfig.themeColor))}</div><div class="small">phases: ${state.q.phases.length} / rewards: ${state.q.rewards.length}</div><div class="small">objectives: ${state.q.phases.reduce((n,p)=>n+(p.objectives?.length||0),0)}</div><div class="small">mode: ${esc(state.q.mode || '-')}</div></div>${state.q.mode === 'COLLECTION' ? renderCollectionPreview(state) : ''}`;
  if (state.ui.tab === 'json') h = `<div class="json">${esc(JSON.stringify(state.q, null, 2))}</div>`;
  if (state.ui.tab === 'validate') h = state.diag.length ? state.diag.map(d => `<div class="diag ${d.lvl}" data-path="${esc(d.path)}"><b>${d.lvl.toUpperCase()}</b><div>${esc(d.msg)}</div><div class="small">${esc(d.path)}</div></div>`).join('') : '<div class="diag info">没有诊断问题</div>';
  if (state.ui.tab === 'help') h = '<div class="sec"><h3>帮助</h3><div class="small">第二轮已加入节点点击联动、分支连线、诊断定位。</div></div>';
  if (state.ui.tab === 'graph') h = renderGraph(state);
  rightEl.innerHTML = h;
  rightEl.querySelectorAll('[data-path]').forEach(el => el.onclick = () => onNavigate(el.dataset.path));
  rightEl.querySelectorAll('[data-phase-index]').forEach(el => el.onclick = () => onGraphPhaseClick(Number(el.dataset.phaseIndex)));
}
