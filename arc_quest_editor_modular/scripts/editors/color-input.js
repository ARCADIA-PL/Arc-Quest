export function renderColorInput(bind, value, allowDefault) {
    const intVal = typeof value === 'number' && !isNaN(value) ? value : 0xFFD700;
    const hex = '#' + (intVal & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
    const isDefault = allowDefault && intVal === -1;

    return `<div class="color-input-row ${isDefault ? 'color-input-disabled' : ''}">
      <input type="color" data-color-picker="${bind}" value="${hex}" class="color-picker" ${isDefault ? 'disabled' : ''}>
      <input data-b="${bind}" value="${isDefault ? -1 : intVal}" class="color-hex-text" placeholder="#FFD700" ${isDefault ? 'disabled' : ''}>
      ${isDefault ? '<span class="color-swatch-placeholder">默认</span>' : `<span class="color-swatch" style="background:${hex}"></span>`}
      ${allowDefault ? '<span class="color-default-tip">-1=默认</span>' : ''}
    </div>`;
}
