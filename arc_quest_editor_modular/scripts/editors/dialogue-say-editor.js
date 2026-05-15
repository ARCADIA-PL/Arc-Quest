import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';

export function renderDialogueSayEditor(node, ni, registry) {
    const prefix = `diag.node.${ni}`;
    return `
    <div class="sec">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
        <button data-goto-node="${ni}" class="toolbar-btn small-btn">← 返回节点</button>
        <h3 style="margin:0">默认文本 · ${esc(node.nodeId || '')}</h3>
      </div>
      ${renderTextSpec(`${prefix}.speaker`, node.speaker || {}, 'Speaker')}
      ${renderTextSpec(`${prefix}.text`, node.text || {}, '默认文本 (无条件的 fallback)')}
    </div>
  `;
}
