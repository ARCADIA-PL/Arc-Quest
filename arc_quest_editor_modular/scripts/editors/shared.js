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
  if (o.type === 'KILL') {
    return suggestInput('entityType', `${base}.targetId`, o.targetId || o.entityType || '', ['minecraft:zombie', 'minecraft:skeleton', 'minecraft:creeper', 'minecraft:spider'], `${base}-entityType`);
  }
  if (o.type === 'COLLECT') {
    return `
      <div class="row">
        ${suggestInput('itemId', `${base}.targetId`, o.targetId || o.itemId || '', ['minecraft:iron_ingot', 'minecraft:stick', 'minecraft:wheat', 'minecraft:oak_log'], `${base}-itemId`)}
        ${field('itemTag', `${base}.itemTag`, o.itemTag || '')}
      </div>
    `;
  }
  if (o.type === 'TALK') {
    return `
      <div class="row">
        ${suggestInput('npcId', `${base}.npcId`, o.npcId || o.targetId || '', ['arc_quest:npc_guard', 'arc_quest:npc_blacksmith', 'arc_quest:npc_villager'], `${base}-npcId`)}
      </div>
    `;
  }
  if (o.type === 'INTERACT') {
    return field('targetId', `${base}.targetId`, o.targetId || '');
  }
  if (o.type === 'OFFER') {
    return `
      <div class="row">
        ${suggestInput('itemId', `${base}.targetId`, o.targetId || '', ['minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:diamond'], `${base}-offer-itemId`)}
        ${field('itemTag', `${base}.itemTag`, o.itemTag || '')}
      </div>
    `;
  }
  if (o.type === 'DELIVER') {
    return `
      <div class="row">
        ${suggestInput('itemId', `${base}.targetId`, o.targetId || '', ['minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:diamond'], `${base}-deliver-itemId`)}
        ${suggestInput('npcId', `${base}.npcId`, o.npcId || '', ['arc_quest:npc_guard', 'arc_quest:npc_blacksmith', 'arc_quest:npc_villager'], `${base}-deliver-npcId`)}
      </div>
    `;
  }
  if (o.type === 'REACH_LOCATION') {
    return `
      <div class="row">
        ${field('x', `${base}.x`, o.x ?? 0, 'number')}
        ${field('y', `${base}.y`, o.y ?? 64, 'number')}
        ${field('z', `${base}.z`, o.z ?? 0, 'number')}
        ${field('radius', `${base}.radius`, o.radius ?? 4, 'number')}
      </div>
    `;
  }
  if (o.type === 'CRAFT') {
    return suggestInput('itemId', `${base}.targetId`, o.targetId || '', ['minecraft:torch', 'minecraft:crafting_table', 'minecraft:iron_sword'], `${base}-craft-itemId`);
  }
  return field('customTargetId', `${base}.targetId`, o.targetId || '');
}

export function renderPhaseModeSummary(phase) {
  return `<div class="small">当前模式：${phase.mode || 'normal'}</div>`;
}

export function renderIdsHint(phase) {
  return `<div class="tiny">parallel: ${(phase.parallelPhaseIds || []).join(', ') || '-'} | choice: ${(phase.choicePhaseIds || []).join(', ') || '-'} | transitions: ${(phase.transitions || []).length}</div>`;
}
