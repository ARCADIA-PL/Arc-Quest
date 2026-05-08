function boolSelect(label, bind, value) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>false</option><option value="true" ${value ? 'selected' : ''}>true</option></select></div>`;
}

function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId) {
  const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
  return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

export function renderObjectiveExtra(o, base, field) {
  if (o.type === 'kill') {
    return suggestInput('entityType', `${base}.entityType`, o.entityType || '', ['minecraft:zombie', 'minecraft:skeleton', 'minecraft:creeper', 'minecraft:spider'], `${base}-entityType`);
  }
  if (o.type === 'collect') {
    return suggestInput('itemId', `${base}.itemId`, o.itemId || '', ['minecraft:iron_ingot', 'minecraft:stick', 'minecraft:wheat', 'minecraft:oak_log'], `${base}-itemId`);
  }
  if (o.type === 'talk') {
    return `
      <div class="row">
        ${suggestInput('npcId', `${base}.npcId`, o.npcId || '', ['arc_quest:npc_guard', 'arc_quest:npc_blacksmith', 'arc_quest:npc_villager'], `${base}-npcId`)}
        ${suggestInput('dialogueId', `${base}.dialogueId`, o.dialogueId || '', ['arc_quest:intro', 'arc_quest:chapter_1', 'arc_quest:quest_hint'], `${base}-dialogueId`)}
      </div>
    `;
  }
  if (o.type === 'interact') {
    return `
      <div class="row">
        ${enumSelect('targetType', `${base}.targetType`, o.targetType || 'entity', ['entity', 'block', 'npc'])}
        ${field('targetId', `${base}.targetId`, o.targetId || '')}
      </div>
    `;
  }
  if (o.type === 'submit') {
    return `
      <div class="row">
        ${suggestInput('itemId', `${base}.itemId`, o.itemId || '', ['minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:diamond'], `${base}-submit-itemId`)}
        ${boolSelect('consumeOnSubmit', `${base}.consumeOnSubmit`, !!o.consumeOnSubmit)}
      </div>
    `;
  }
  if (o.type === 'custom_counter') {
    return field('counterId', `${base}.counterId`, o.counterId || '');
  }
  return `
    <div class="row">
      ${field('x', `${base}.x`, o.x ?? 0, 'number')}
      ${field('y', `${base}.y`, o.y ?? 64, 'number')}
      ${field('z', `${base}.z`, o.z ?? 0, 'number')}
    </div>
  `;
}

export function renderPhaseModeSummary(phase) {
  return `<div class="small">当前模式：${phase.mode || 'normal'}</div>`;
}

export function renderIdsHint(phase) {
  return `<div class="tiny">parallel: ${(phase.parallelPhaseIds || []).join(', ') || '-'} | choice: ${(phase.choicePhaseIds || []).join(', ') || '-'} | transitions: ${(phase.transitions || []).length}</div>`;
}
