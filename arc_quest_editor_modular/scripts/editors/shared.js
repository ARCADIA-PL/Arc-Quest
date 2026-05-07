function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

export function renderObjectiveExtra(o, base, field) {
  if (o.type === 'kill') return field('entityType', `${base}.entityType`, o.entityType || '');
  if (o.type === 'collect') return field('itemId', `${base}.itemId`, o.itemId || '');
  if (o.type === 'talk') return field('dialogueId', `${base}.dialogueId`, o.dialogueId || '') + field('npcId', `${base}.npcId`, o.npcId || '');
  if (o.type === 'interact') return enumSelect('targetType', `${base}.targetType`, o.targetType || 'entity', ['entity', 'block', 'npc']) + field('targetId', `${base}.targetId`, o.targetId || '');
  if (o.type === 'submit') return field('itemId', `${base}.itemId`, o.itemId || '') + boolSelect('consumeOnSubmit', `${base}.consumeOnSubmit`, !!o.consumeOnSubmit);
  if (o.type === 'custom_counter') return field('counterId', `${base}.counterId`, o.counterId || '');
  return field('x', `${base}.x`, o.x ?? 0, 'number') + field('y', `${base}.y`, o.y ?? 64, 'number') + field('z', `${base}.z`, o.z ?? 0, 'number');
}

export function renderPhaseModeSummary(phase) {
  return `<div class="small">当前模式：${phase.mode || 'normal'}</div>`;
}

export function renderIdsHint(phase) {
  return `<div class="tiny">parallel: ${(phase.parallelPhaseIds || []).join(', ') || '-'} | choice: ${(phase.choicePhaseIds || []).join(', ') || '-'} | transitions: ${(phase.transitions || []).length}</div>`;
}
