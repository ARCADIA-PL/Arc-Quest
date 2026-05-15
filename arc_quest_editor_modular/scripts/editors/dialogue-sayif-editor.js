import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderConditionTree} from './condition-editor.js';

export function renderDialogueSayIfEditor(node, key, sayIf, ni, registry, dialogue) {
    const sp = `diag.node.${ni}.condText.${key}`;
    const nodeObj = dialogue.nodes[ni];
    const entries = nodeObj ? Object.entries(nodeObj.conditionalTexts || {}) : [];
    const keys = entries.map(([k]) => k);
    const curIdx = keys.indexOf(key);
    const total = keys.length;
    const prevKey = curIdx > 0 ? keys[curIdx - 1] : null;
    const nextKey = curIdx < total - 1 ? keys[curIdx + 1] : null;

    return `
    <div class="sec">
      <div class="breadcrumb">
        <span data-goto-config class="breadcrumb-link">⚙ 对话配置</span>
        <span class="breadcrumb-sep">›</span>
        <span data-goto-node="${ni}" class="breadcrumb-link">${esc(node.nodeId || '')}</span>
        <span class="breadcrumb-sep">›</span>
        <span class="breadcrumb-current">${esc(key)}</span>
      </div>
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <div style="display:flex;gap:6px">
          ${prevKey ? `<button data-nav-sayif="${ni}:${prevKey}" class="toolbar-btn small-btn">◀ ${esc(prevKey)}</button>` : '<span class="toolbar-btn small-btn" style="opacity:.3">◀</span>'}
          ${nextKey ? `<button data-nav-sayif="${ni}:${nextKey}" class="toolbar-btn small-btn">${esc(nextKey)} ▶</button>` : '<span class="toolbar-btn small-btn" style="opacity:.3">▶</span>'}
        </div>
        <span class="tiny" style="opacity:.5">${curIdx + 1}/${total}</span>
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
        <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
        ${(sayIf.conditions || []).map((cond, ci) =>
            renderConditionTree(`${sp}.cond.${ci}`, cond, registry, true)
        ).join('')}
        <div class="actions"><button data-cond-append="${sp}" data-cond-skip-rerender="true">＋ 添加条件</button></div>
      </div>
      <div class="actions" style="margin-top:12px">
        <button data-ddcondtext="${ni}:${key}">删除此 SayIf</button>
      </div>
    </div>
  `;
}
