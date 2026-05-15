import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderConditionTree} from './condition-editor.js';

export function renderDialogueSayIfEditor(node, key, sayIf, ni, registry) {
    const sp = `diag.node.${ni}.condText.${key}`;
    const condSummary = (sayIf.conditions || []).map(c => {
        if (c.condition === 'arc_quest:always') return 'always';
        if (c.condition === 'arc_quest:not' && c.inner) return `NOT(${c.inner.condition || '?'})`;
        return c.condition ? c.condition.replace('arc_quest:', '') : '?';
    }).join(', ') || '无';

    return `
    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
        <button data-goto-node="${ni}" class="toolbar-btn small-btn">← 返回节点</button>
        <h3 style="margin:0">SayIf: ${esc(key)} · ${esc(node.nodeId || '')}</h3>
      </div>
      <div class="row">
        <div class="f"><label>Identifier</label>
          <input value="${esc(key)}" disabled style="opacity:.6" title="Map key，不可修改">
        </div>
        <div class="f"><label>Say ID</label><input data-b="${sp}.sayId" value="${esc(sayIf.sayId || '')}"></div>
        <div class="f"><label>Priority</label><input type="number" data-b="${sp}.priority" value="${sayIf.priority ?? 0}" min="0"></div>
      </div>
      <div class="row">
        <div class="f"><label>Sound</label><input data-b="${sp}.soundEvent" value="${esc(sayIf.soundEvent || '')}" placeholder="minecraft:entity.villager.yes"></div>
      </div>
      ${renderTextSpec(`${sp}.text`, sayIf.text || {}, '条件匹配时显示的文本')}
      <div style="margin-top:8px">
        <div class="small" style="margin-bottom:4px"><b>Conditions (${condSummary})</b></div>
        ${(sayIf.conditions || []).map((cond, ci) =>
            renderConditionTree(`${sp}.cond.${ci}`, cond, registry, true)
        ).join('')}
        <div class="actions"><button data-cond-append="${sp}">＋ 添加条件</button></div>
      </div>
      <div class="actions" style="margin-top:12px">
        <button data-ddcondtext="${ni}:${key}">删除此 SayIf</button>
      </div>
    </div>
  `;
}
