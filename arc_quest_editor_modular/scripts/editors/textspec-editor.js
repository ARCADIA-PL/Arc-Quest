import {esc} from '../core/utils.js';

export function renderTextSpec(bindBase, spec, label) {
    const mode = spec?.mode || 'literal';
    const value = spec?.value || '';
    const args = spec?.args || [];

    const chips = args.map((v, i) =>
        `<span class="chip-item">${esc(v)}<button type="button" class="chip-remove" data-chip-remove="${bindBase}.args:${i}">×</button></span>`
    ).join('');

    return `
    <div class="card">
      <div class="small"><b>${label}</b></div>
      <div class="row">
        <div class="f">
          <label>Mode</label>
          <select data-b="${bindBase}.mode">
            <option value="literal" ${mode === 'literal' ? 'selected' : ''}>字面</option>
            <option value="translatable" ${mode === 'translatable' ? 'selected' : ''}>可翻译</option>
          </select>
        </div>
        <div class="f"><label>Value</label>
          <input data-b="${bindBase}.value" value="${esc(value)}">
        </div>
      </div>
      ${mode === 'translatable' ? `
      <div class="f" style="margin-top:6px">
        <label>Args</label>
        <div class="chip-editor" data-chip-add-wrap="${bindBase}.args">
          <div class="chip-list">${chips || '<span class="tiny">内置: player_name, npc_name, npc_pos, npc_display_name</span>'}</div>
          <div class="chip-input-row">
            <input type="text" data-chip-add-input="${bindBase}.args" placeholder="player_name">
            <button type="button" data-chip-add="${bindBase}.args">添加</button>
          </div>
        </div>
      </div>` : ''}
    </div>`;
}
