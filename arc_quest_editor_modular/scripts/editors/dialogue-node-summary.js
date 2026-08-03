import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';
import {renderMarkEditor} from './mark-editor.js';

function condChipClasses(condition) {
    const cond = condition?.condition || '';
    if (cond === 'arc_quest:has_flag') return 'cond-chip flag';
    if (cond === 'arc_quest:not_has_flag') return 'cond-chip not-flag';
    if (cond.startsWith('arc_quest:quest_phase')) return 'cond-chip quest';
    if (cond === 'arc_quest:quest_completed' || cond === 'arc_quest:quest_accepted') return 'cond-chip quest-done';
    if (cond === 'arc_quest:variable_check') return 'cond-chip var';
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
        return `<span class="${condChipClasses(c)}">${condLabel(c)}${c.questId ? ': ' + esc(c.questId.substring(0, 24)) : ''}${c.flag ? ': ' + esc(c.flag) : ''}${c.phaseId ? '/' + esc(c.phaseId.substring(0, 16)) : ''}${c.key ? ' ' + esc(c.key) : ''}</span>`;
    }).join('');
}

export function renderDialogueNodeDetail(dialogue, node, ni, registry, state) {
    const prefix = `diag.node.${ni}`;
    const nodeIds = dialogue.nodes.map(n => n.nodeId).filter(Boolean);
    const star = dialogue.startNodeId === node.nodeId ? ' ★ 起始节点' : '';
    const sayIfEntries = Object.entries(node.conditionalTexts || {});
    const choices = node.choices || [];
    const sayIfFold = !!(state?.dialogue?.ui?.sayIfFold);
    const choiceFold = !!(state?.dialogue?.ui?.choiceFold);

    const sayIfTotal = sayIfEntries.length + 1;

    const defaultCardHtml = `<div class="detail-card sayif-anchor-card">
      <div style="display:flex;align-items:center;gap:8px">
        <b class="small" style="flex:1">🟢 默认</b>
        <span class="tiny" style="opacity:.5">always</span>
      </div>
      <div class="small" style="color:var(--text-mut);margin:4px 0">${esc((node.text?.value || '').substring(0, 60))}${(node.text?.value || '').length > 60 ? '…' : ''}</div>
      <div style="text-align:right">
        <button data-edit-sayif="${ni}::default" class="toolbar-btn small-btn">编辑</button>
      </div>
    </div>`;

    const sayIfCards = defaultCardHtml + sayIfEntries.map(([key, say]) => {
        return `<div class="detail-card">
          <div style="display:flex;align-items:center;gap:8px">
            <b class="small" style="flex:1">${esc(key)}</b>
            <span class="tiny" style="opacity:.5">pri:${say.priority ?? 0}</span>
            <div style="display:flex;gap:4px">${renderCondChips(say.conditions)}</div>
            <button data-ddcondtext-card="${ni}:${key}" class="toolbar-btn small-btn" style="color:var(--danger);margin-left:4px" title="删除此 SayIf">✕</button>
          </div>
          <div class="small" style="color:var(--text-mut);margin:4px 0">${esc((say.text?.value || '').substring(0, 60))}${(say.text?.value || '').length > 60 ? '…' : ''}</div>
          <div style="text-align:right">
            <button data-edit-sayif="${ni}:${key}" class="toolbar-btn small-btn">编辑</button>
          </div>
        </div>`;
    }).join('');

    const choiceCards = choices.map((ch, ci) => {
        const actCount = (ch.actions || []).length;
        const nextLabel = ch.nextNodeId ? `→ ${esc(ch.nextNodeId)}` : '→ —';
        return `<div class="detail-card">
          <div style="display:flex;align-items:center;gap:8px">
            <b class="small" style="flex:1">${esc(ch.choiceId || `#${ci + 1}`)}</b>
            <span class="tiny" style="opacity:.7">${nextLabel}</span>
            <span class="tiny" style="opacity:.5">act:${actCount}</span>
            <button data-dchoice-del-card="${ni}:${ci}" class="toolbar-btn small-btn" style="color:var(--danger);margin-left:4px" title="删除此 Choice">✕</button>
          </div>
          <div class="small" style="color:var(--text-mut);margin:4px 0">${esc((ch.text?.value || '').substring(0, 60))}${(ch.text?.value || '').length > 60 ? '…' : ''}</div>
          <div style="text-align:right">
            <button data-edit-choice="${ni}:${ci}" class="toolbar-btn small-btn">编辑</button>
          </div>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <div class="breadcrumb">
        <span data-goto-config class="breadcrumb-link">⚙ 对话配置</span>
        <span class="breadcrumb-sep">›</span>
        <span class="breadcrumb-current">${esc(node.nodeId)}${star}</span>
      </div>
      <h3 style="margin-top:4px">节点属性</h3>
      <div class="row">
        <div class="f"><label>Node ID</label><input data-b="${prefix}.nodeId" value="${esc(node.nodeId || '')}"></div>
        <div class="f"><label>Delay (ms)</label><input type="number" data-b="${prefix}.delayMs" value="${node.delayMs ?? 0}" min="0" max="30000"></div>
      </div>
      <div class="row">
        <div class="f"><label>Auto Next ID</label>
          <input data-b="${prefix}.autoNextId" list="${prefix}-autoNext-list" value="${esc(node.autoNextId || '')}" placeholder="可选">
          <datalist id="${prefix}-autoNext-list">${nodeIds.map(v => `<option value="${v}"></option>`).join('')}</datalist>
        </div>
        <div class="f"><label>Enter Sound</label><input data-b="${prefix}.nodeEnterSound" value="${esc(node.nodeEnterSound || '')}" placeholder="minecraft:entity.villager.yes"></div>
      </div>
      ${renderTextSpec(`${prefix}.speaker`, node.speaker || {}, 'Speaker')}
      ${renderCooldownGroup(prefix, node)}
      ${renderMarkEditor(node.relatedMarks, `${prefix}.relatedMarks`, '进入节点时触发的 Marker')}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">SayIf · ${sayIfTotal}</h3>
        <button id="addCondTextBtn_${ni}" class="toolbar-btn small-btn">＋ 添加</button>
        <button data-toggle-sayif-fold="${ni}" class="toolbar-btn small-btn" style="margin-left:auto">${sayIfFold ? '▾ 展开' : '▴ 收起'}</button>
      </div>
      ${!sayIfFold ? (sayIfCards || '<div class="small" style="color:var(--text-mut)">暂无条件文本</div>') : ''}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">Choices (${choices.length})</h3>
        <button data-choice-add="${ni}" class="toolbar-btn small-btn">＋ 添加</button>
        <button data-toggle-choice-fold="${ni}" class="toolbar-btn small-btn" style="margin-left:auto">${choiceFold ? '▾ 展开' : '▴ 收起'}</button>
      </div>
      ${!choiceFold ? (choiceCards || '<div class="small" style="color:var(--text-mut)">暂无选项</div>') : ''}
    </div>

    <div class="actions" style="margin-top:12px"><button data-ddnode="${ni}">删除此节点</button></div>
  `;
}
