function modeSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

export function renderPhaseHeaderSection(s, p, field, area) {
  return `
    <div class="card">
      ${field('阶段标识 (Phase ID)', `ph.${s.pi}.id`, p.id)}
      <div class="row">
        ${field('标题 (Title)', `ph.${s.pi}.title`, p.title || '')}
        ${modeSelect('Title Mode', `ph.${s.pi}.titleMode`, p.titleMode || 'translatable')}
      </div>
      <div class="row">
        ${area('描述 (Description)', `ph.${s.pi}.description`, p.description || '')}
        ${modeSelect('Description Mode', `ph.${s.pi}.descriptionMode`, p.descriptionMode || 'translatable')}
      </div>
    </div>
  `;
}
