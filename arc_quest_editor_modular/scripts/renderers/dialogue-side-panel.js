import {esc} from '../core/utils.js';

function condChipClasses(condition) {
    const cond = condition?.condition || '';
    if (cond === 'arc_quest:has_flag') return 'cond-chip flag';
    if (cond === 'arc_quest:not_has_flag') return 'cond-chip not-flag';
    if (cond.startsWith('arc_quest:quest_phase')) return 'cond-chip quest';
    if (cond === 'arc_quest:quest_completed' || cond === 'arc_quest:quest_accepted') return 'cond-chip quest-done';
    if (cond === 'arc_quest:not') return 'cond-chip not';
    if (cond === 'arc_quest:and' || cond === 'arc_quest:or') return 'cond-chip logic';
    return 'cond-chip';
}

function condLabel(condition) {
    const cond = condition?.condition || 'always';
    if (cond === 'arc_quest:always') return 'always';
    return cond.replace('arc_quest:', '');
}

function renderCondChips(conditions) {
    if (!conditions || conditions.length === 0) return '<span class="cond-chip">always</span>';
    return conditions.map(c => {
        if (c.condition === 'arc_quest:not' && c.inner) {
            return `<span class="cond-chip not">NOT</span><span class="${condChipClasses(c.inner)}">${condLabel(c.inner)}</span>`;
        }
        return `<span class="${condChipClasses(c)}">${condLabel(c)}${c.questId ? ': ' + esc(c.questId.substring(0, 20)) : ''}${c.flag ? ': ' + esc(c.flag) : ''}${c.phaseId ? '/' + esc(c.phaseId.substring(0, 14)) : ''}</span>`;
    }).join('');
}

export function renderDialogueDirectory(state) {
    const sel = state.dialogue.ui.sel;
    const dialogue = state.dialogue.q;
    const nodes = dialogue.nodes || [];

    if (sel.t === 'config') {
        const nodeRows = nodes.map((node, ni) => {
            const isStart = dialogue.startNodeId === node.nodeId;
            const star = isStart ? ' ★' : '';
            const siCount = Object.keys(node.conditionalTexts || {}).length;
            const chCount = (node.choices || []).length;
            return `<div class="detail-card dir-entry" data-dir-goto="node" data-dir-ni="${ni}">
              <div class="small"><b>${esc(node.nodeId || `#${ni + 1}`)}${star}</b></div>
              <div class="tiny" style="opacity:.5;margin-top:2px">S:${siCount} C:${chCount}</div>
            </div>`;
        }).join('');
        return `
        <div class="sec">
          <h3>节点概览 (${nodes.length})</h3>
          ${nodeRows || '<div class="small" style="color:var(--text-mut)">暂无节点</div>'}
        </div>`;
    }

    const ni = typeof sel.ni === 'number' ? sel.ni : -1;
    const node = nodes[ni];
    if (!node) return '<div class="sec"><div class="small" style="color:var(--text-mut)">请选择节点</div></div>';

    const sayIfEntries = Object.entries(node.conditionalTexts || {});
    const choices = node.choices || [];
    const star = dialogue.startNodeId === node.nodeId ? ' ★ 起始节点' : '';

    const defaultDirEntry = `<div class="detail-card dir-entry sayif-anchor-card ${sel.t === 'sayIf' && sel.key === ':default' ? 'on' : ''}" data-dir-goto="sayIf" data-dir-ni="${ni}" data-dir-key=":default">
      <div class="small" style="display:flex;align-items:center;gap:4px">
        <b>🟢 默认</b>
        <span class="tiny" style="opacity:.4">always</span>
      </div>
      <div class="tiny" style="color:var(--text-mut);margin-top:3px">${esc((node.text?.value || '').substring(0, 50))}${(node.text?.value || '').length > 50 ? '…' : ''}</div>
    </div>`;

    const sayIfList = defaultDirEntry + sayIfEntries.map(([key, say]) => {
        const isActive = sel.t === 'sayIf' && sel.key === key;
        return `<div class="detail-card dir-entry ${isActive ? 'on' : ''}" data-dir-goto="sayIf" data-dir-ni="${ni}" data-dir-key="${key}">
          <div class="small" style="display:flex;align-items:center;gap:4px">
            <b>${esc(key)}</b>
            <span class="tiny" style="opacity:.4">pri:${say.priority ?? 0}</span>
            <div style="display:flex;gap:3px;margin-left:auto">${renderCondChips(say.conditions)}</div>
          </div>
          <div class="tiny" style="color:var(--text-mut);margin-top:3px">${esc((say.text?.value || '').substring(0, 50))}${(say.text?.value || '').length > 50 ? '…' : ''}</div>
        </div>`;
    }).join('');

    const choiceList = choices.map((ch, ci) => {
        const isActive = sel.t === 'choice' && sel.ci === ci;
        const nextLabel = ch.nextNodeId || '—';
        const actCount = (ch.actions || []).length;
        return `<div class="detail-card dir-entry ${isActive ? 'on' : ''}" data-dir-goto="choice" data-dir-ni="${ni}" data-dir-ci="${ci}">
          <div class="small" style="display:flex;align-items:center;gap:4px">
            <b>${esc(ch.choiceId || `#${ci + 1}`)}</b>
            <span class="tiny" style="opacity:.4">→ ${esc(nextLabel)}</span>
            <span class="tiny" style="opacity:.4;margin-left:auto">act:${actCount}</span>
          </div>
          <div class="tiny" style="color:var(--text-mut);margin-top:3px">${esc((ch.text?.value || '').substring(0, 50))}${(ch.text?.value || '').length > 50 ? '…' : ''}</div>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <h3>${esc(node.nodeId)}${star}</h3>
      <h4 style="margin-top:8px;margin-bottom:4px">SayIf (${sayIfEntries.length + 1})</h4>
      ${sayIfList || '<div class="small" style="color:var(--text-mut)">暂无</div>'}
      <h4 style="margin-top:10px;margin-bottom:4px">Choices (${choices.length})</h4>
      ${choiceList || '<div class="small" style="color:var(--text-mut)">暂无</div>'}
    </div>`;
}

export function bindDialogueDirectoryClicks(rightEl, state, rerender) {
    rightEl.querySelectorAll('.dir-entry').forEach(el => {
        el.onclick = () => {
            const ds = el.dataset;
            if (ds.dirGoto === 'node' && ds.dirNi !== undefined) {
                state.dialogue.ui.sel = {t: 'node', ni: +ds.dirNi};
                rerender();
            } else if (ds.dirGoto === 'sayIf' && ds.dirNi !== undefined && ds.dirKey) {
                state.dialogue.ui.sel = {t: 'sayIf', ni: +ds.dirNi, key: ds.dirKey};
                rerender();
            } else if (ds.dirGoto === 'choice' && ds.dirNi !== undefined && ds.dirCi !== undefined) {
                state.dialogue.ui.sel = {t: 'choice', ni: +ds.dirNi, ci: +ds.dirCi};
                rerender();
            }
        };
    });
}
