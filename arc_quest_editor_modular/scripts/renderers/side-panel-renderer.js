import {esc, clr, buildReferenceIndex, resolveSelectionRefKey} from '../core/utils.js';
import {renderGraph, bindGraphEvents, bindGraphShellEvents} from './graph-renderer.js';

export function renderSidePanel(state, tabsEl, rightEl, onTabChange, onNavigate, onGraphPhaseClick) {
    const ns = {graph: '拓扑', refs: '引用', cross: '跨文件', preview: '大纲', validate: '诊断', json: 'JSON', help: '指南'};
    tabsEl.innerHTML = Object.keys(ns).map(k => `<div class="tab ${state.quest.ui.tab === k ? 'active' : ''}" data-t="${k}">${ns[k]}</div>`).join('');
    tabsEl.onclick = e => {
        const t = e.target.dataset.t;
        if (t) onTabChange(t);
    };

    let h = '';
    const wrap = content => `<div class="fade-in" style="height:100%; display:flex; flex-direction:column;">${content}</div>`;

    if (state.quest.ui.tab === 'preview') h = wrap(`<div class="sec"><h3 style="color:var(--text-main); font-size:14px; font-weight:600; text-transform:none;">${esc(state.quest.q.id)}</h3><div class="card"><div class="small" style="margin-bottom:8px;"><b>Title:</b> ${esc(state.quest.q.title)}</div><div class="small" style="margin-bottom:8px;"><b>Theme:</b> <span class="chip" style="background:${esc(clr(state.quest.q.visualConfig.themeColor))}20; color:${esc(clr(state.quest.q.visualConfig.themeColor))}">${esc(clr(state.quest.q.visualConfig.themeColor))}</span></div><div class="small" style="margin-bottom:8px;"><b>Scale:</b> ${state.quest.q.phases.length} Phases / ${state.quest.q.rewards.length} Rewards</div><div class="small"><b>Mode:</b> ${esc(state.quest.q.mode || 'Standard')}</div></div></div>`);
    if (state.quest.ui.tab === 'json') h = wrap(`<div class="json" style="flex:1; overflow:auto">${esc(JSON.stringify(state.quest.q, null, 2))}</div>`);
    if (state.quest.ui.tab === 'validate') {
        h = wrap(state.quest.diag.length ? `<div class="sec"><h3 style="color:var(--danger)">Detected Issues (${state.quest.diag.length})</h3>` + state.quest.diag.map(d => `<div class="diag ${d.lvl}" data-path="${esc(d.path)}"><b style="display:block; margin-bottom:4px; font-size:11px;">[${d.lvl.toUpperCase()}]</b><div style="color:var(--text-main); font-weight:500">${esc(d.msg)}</div><div class="tiny" style="margin-top:6px; font-family:monospace">${esc(d.path)}</div></div>`).join('') + `</div>` : '<div class="sec"><div class="diag info" style="text-align:center; padding: 24px;"><svg viewBox="0 0 24 24" width="32" height="32" stroke="var(--success)" stroke-width="2" fill="none" style="margin-bottom:12px;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg><div style="font-size:14px; color:var(--success); font-weight:600">系统诊断通过</div><div class="small" style="margin-top:8px">未发现任何配置结构异常。</div></div></div>');
    }
    if (state.quest.ui.tab === 'refs') {
        const refIndex = buildReferenceIndex(state.quest.q);
        const key = resolveSelectionRefKey(state);
        const incoming = refIndex.incoming.get(key) || [];
        const outgoing = refIndex.outgoing.get(key) || [];
        const unresolved = refIndex.unresolved || [];
        h = wrap(`
      <div class="sec">
        <h3>引用关系</h3>
        <div class="card">
          <div class="small"><b>当前对象：</b>${esc(key || '-')}</div>
          <div class="small" style="margin-top:6px"><b>入向引用：</b>${incoming.length} 条</div>
          ${incoming.length ? incoming.map(r => `<div class="diag info" data-path="${esc(r.path)}"><div><b>${esc(r.kind)}</b>：${esc(r.from)} → ${esc(r.to)}</div><div class="tiny">点击跳转来源</div></div>`).join('') : '<div class="tiny" style="margin-top:8px">无</div>'}
          <div class="small" style="margin-top:10px"><b>出向引用：</b>${outgoing.length} 条</div>
          ${outgoing.length ? outgoing.map(r => `<div class="diag" data-path="${esc(r.path)}"><div><b>${esc(r.kind)}</b>：${esc(r.from)} → ${esc(r.to)}</div></div>`).join('') : '<div class="tiny" style="margin-top:8px">无</div>'}
        </div>
        <div class="card" style="margin-top:12px">
          <div class="small"><b>失效引用：</b>${unresolved.length} 条</div>
          ${unresolved.length ? unresolved.map(r => `<div class="diag warn" data-path="${esc(r.path)}"><div><b>${esc(r.kind)}</b>：${esc(r.from)} → ${esc(r.to)}</div></div>`).join('') : '<div class="tiny" style="margin-top:8px">无</div>'}
        </div>
      </div>
    `);
    }
    const LEVEL_LABELS = {err: '错误', warn: '警告', info: '信息'};
    if (state.quest.ui.tab === 'cross') {
        const result = state.quest.crossResults;
        if (!result) {
            h = wrap(`<div class="sec"><div class="diag info" style="text-align:center; padding: 24px;">
                <div style="font-size:14px; color:var(--text-mut); font-weight:600">尚未执行跨文件校验</div>
                <div class="small" style="margin-top:8px">点击工具栏"跨文件校验"按钮</div>
            </div></div>`);
        } else {
            const {issues, summary} = result;
            const errCount = summary.totalErrors;
            const warnCount = summary.totalWarnings;
            const infoCount = summary.totalInfos;
            const statusColor = errCount > 0 ? 'var(--danger)' : 'var(--success)';
            const statusIcon = errCount > 0
                ? '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"/>'
                : '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>';
            h = wrap(`
                <div class="sec">
                    <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;">
                        <svg viewBox="0 0 24 24" width="24" height="24" stroke="${statusColor}" stroke-width="2" fill="none">${statusIcon}</svg>
                        <span style="color:${statusColor};font-weight:600;font-size:14px;">
                            ${errCount > 0 ? `${errCount} 个跨文件错误` : '跨文件引用完整'}
                        </span>
                    </div>
                    <div class="card" style="margin-bottom:12px;">
                        <div class="small"><b>注册表规模：</b>${summary.questCount} 任务 / ${summary.dialogueCount} 对话 / ${summary.npcCount} NPC</div>
                        <div class="small" style="margin-top:4px;">
                            <span style="color:${errCount > 0 ? 'var(--danger)' : 'var(--text-mut)'}">错误 ${errCount}</span>
                            &nbsp;·&nbsp;
                            <span style="color:${warnCount > 0 ? 'var(--warning)' : 'var(--text-mut)'}">警告 ${warnCount}</span>
                            &nbsp;·&nbsp;
                            <span style="color:var(--text-mut)">信息 ${infoCount}</span>
                        </div>
                    </div>
                    ${issues.length === 0 ? '<div class="diag info" style="text-align:center;padding:16px;"><span style="color:var(--success);font-weight:600;">无跨文件引用问题</span></div>' : ''}
                    ${issues.map(d => `
                        <div class="diag ${d.lvl}">
                            <b style="display:block;margin-bottom:4px;font-size:11px;">
                                [${d.src.type}:${esc(d.src.id)}]
                                <span style="margin-left:6px;opacity:.8;font-size:10px;">${LEVEL_LABELS[d.lvl] || d.lvl.toUpperCase()}</span>
                            </b>
                            <div style="color:var(--text-main);font-weight:500">${esc(d.msg)}</div>
                            <div class="tiny" style="margin-top:6px;font-family:monospace">${esc(d.path)}</div>
                        </div>
                    `).join('')}
                </div>
            `);
        }
    }
    if (state.quest.ui.tab === 'help') h = wrap('<div class="sec"><h3>Quick Guide</h3><div class="card" style="line-height:1.6"><b style="color:var(--text-main)">Pro Update:</b><br/>已采用全新流式界面引擎。<br/>- 支持沉浸式焦点编辑<br/>- 拓扑图支持智能点选联动<br/>- 诊断列表支持一键寻址定位<br/>- 全局支持亚克力磨砂透视。</div></div>');
    if (state.quest.ui.tab === 'graph') h = wrap(renderGraph(state));

    rightEl.innerHTML = h;
    rightEl.querySelectorAll('[data-path]').forEach(el => el.onclick = () => onNavigate(el.dataset.path));

    if (state.quest.ui.tab === 'graph') {
        const shell = rightEl.querySelector('.graph-shell');
        if (shell) {
            const compact = shell.querySelector('.graph-viewport-compact');
            if (compact) bindGraphEvents(compact, state, onGraphPhaseClick);
            bindGraphShellEvents(shell, state, onGraphPhaseClick);
        }
    }
}
