export function chipEditor(label, list, addKey, removeKey, placeholder) {
  const chips = (list || []).map((v, i) => `<span class="chip-item">${v}<button type="button" class="chip-remove" data-chip-remove="${removeKey}:${i}">×</button></span>`).join('');
  return `
    <div class="f">
      <label>${label}</label>
      <div class="chip-editor" data-chip-add-wrap="${addKey}">
        <div class="chip-list">${chips || '<span class="tiny">暂无</span>'}</div>
        <div class="chip-input-row">
          <input type="text" data-chip-add-input="${addKey}" placeholder="${placeholder}">
          <button type="button" data-chip-add="${addKey}">添加</button>
        </div>
      </div>
    </div>
  `;
}
