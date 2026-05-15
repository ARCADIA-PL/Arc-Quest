import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderCooldownGroup} from './cooldown-editor.js';

export function renderDialogueNodeSummary(dialogue, node, ni, registry) {
    const prefix = `diag.node.${ni}`;
    const nodeIds = dialogue.nodes.map(n => n.nodeId).filter(Boolean);
    const star = dialogue.startNodeId === node.nodeId ? ' · ★ 起始节点' : '';

    const sayIfEntries = Object.entries(node.conditionalTexts || {});
    const choices = node.choices || [];

    const sayIfRows = sayIfEntries.map(([key, say]) => {
        const condSummary = (say.conditions || []).map(c => {
            if (c.condition === 'arc_quest:always') return 'always';
            if (c.condition === 'arc_quest:not' && c.inner) return `NOT(${c.inner.condition || '?'})`;
            return c.condition ? c.condition.replace('arc_quest:', '') : '?';
        }).join(', ') || 'always';
        return `<div class="summary-row">
          <div class="small" style="flex:1"><b>${esc(key)}</b> · pri=${say.priority ?? 0} · ${esc(condSummary)}</div>
          <div class="small" style="color:var(--text-mut)">${esc((say.text?.value || '').substring(0, 40))}${(say.text?.value || '').length > 40 ? '…' : ''}</div>
          <button data-edit-sayif="${ni}:${key}" class="toolbar-btn small-btn" style="margin-left:8px">编辑</button>
        </div>`;
    }).join('');

    const choiceRows = choices.map((ch, ci) => {
        const actCount = (ch.actions || []).length;
        const nextLabel = ch.nextNodeId || '—';
        return `<div class="summary-row">
          <div class="small" style="flex:1"><b>${esc(ch.choiceId || `#${ci + 1}`)}</b> → ${esc(nextLabel)} · act:${actCount}</div>
          <div class="small" style="color:var(--text-mut)">${esc((ch.text?.value || '').substring(0, 40))}${(ch.text?.value || '').length > 40 ? '…' : ''}</div>
          <button data-edit-choice="${ni}:${ci}" class="toolbar-btn small-btn" style="margin-left:8px">编辑</button>
        </div>`;
    }).join('');

    return `
    <div class="sec">
      <h3>Node: ${esc(node.nodeId)}${star}</h3>
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
      ${renderTextSpec(`${prefix}.text`, node.text || {}, '默认文本')}
      ${renderCooldownGroup(prefix, node)}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">条件文本 (${sayIfEntries.length})</h3>
        <button id="addCondTextBtn_${ni}" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${sayIfRows || '<div class="small" style="color:var(--text-mut)">暂无条件文本</div>'}
    </div>

    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <h3 style="margin:0">选项 (${choices.length})</h3>
        <button data-choice-add="${ni}" class="toolbar-btn small-btn">＋ 添加</button>
      </div>
      ${choiceRows || '<div class="small" style="color:var(--text-mut)">暂无选项</div>'}
    </div>

    <div class="actions" style="margin-top:12px"><button data-ddnode="${ni}">删除此节点</button></div>
  `;
}
