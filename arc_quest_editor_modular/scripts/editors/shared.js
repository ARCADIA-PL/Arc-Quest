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

function isObjectiveCountingType(type) {
  return type === 'KILL' || type === 'COLLECT' || type === 'DELIVER' || type === 'CRAFT' || type === 'OFFER' || type === 'CUSTOM';
}

function hasAdvancedCountSettings(objective) {
  return !!(
    objective?.countMode && objective.countMode !== 'fixed'
    || objective?.countBase !== undefined && objective.countBase !== null && objective.countBase !== (objective.count ?? 1)
    || objective?.countPerLevel
    || objective?.countMin !== undefined && objective.countMin !== null && objective.countMin !== 1
    || objective?.countMax !== undefined && objective.countMax !== null && objective.countMax !== -1
  );
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
  if (o.type === 'NULL') {
    return '<div class="small">该目标类型不需要 targetId；通常与手动确认推进阶段搭配使用。</div>';
  }
  return field('customTargetId', `${base}.targetId`, o.targetId || '');
}

export function renderObjectiveCommonSection(o, base, field) {
  const supportsCount = isObjectiveCountingType(o.type);
  const showAdvancedCount = supportsCount && hasAdvancedCountSettings(o);
  return `
    <div class="row">
      ${supportsCount ? field('目标数量', `${base}.count`, o.count ?? 1, 'number') : '<div class="f"><label>目标数量</label><div class="tiny">该类型固定为一次触发</div></div>'}
      ${(o.type === 'INTERACT' || o.type === 'TALK' || o.type === 'DELIVER') ? field('targetId（兼容字段）', `${base}.targetId`, o.targetId || '') : '<div class="f"></div>'}
    </div>
    <div class="row">
      ${boolSelect('Hidden', `${base}.hidden`, !!o.hidden)}
      ${boolSelect('Optional', `${base}.optional`, !!o.optional)}
    </div>
    ${supportsCount ? `
    <div class="card" style="margin-top:10px; border-color: rgba(255,255,255,0.08)">
      <div class="small"><b>高级计数设置</b></div>
      <div class="tiny" style="margin-top:4px">仅当目标需求需要按等级/变量动态缩放时启用。常规 objective 只使用“目标数量”。</div>
      ${showAdvancedCount ? `
      <div class="row" style="margin-top:10px">
        ${enumSelect('countMode', `${base}.countMode`, o.countMode || 'fixed', ['fixed', 'level_scale', 'variable'])}
        ${field('countBase', `${base}.countBase`, o.countBase ?? o.count ?? 1, 'number')}
      </div>
      <div class="row">
        ${field('countPerLevel', `${base}.countPerLevel`, o.countPerLevel ?? 0, 'number')}
        ${field('countMin', `${base}.countMin`, o.countMin ?? 1, 'number')}
      </div>
      <div class="row">
        ${field('countMax', `${base}.countMax`, o.countMax ?? -1, 'number')}
        <div class="f"></div>
      </div>
      ` : '<div class="small" style="margin-top:10px">当前未启用高级计数字段；如导入了带动态计数的 objective，会自动显示。</div>'}
    </div>
    ` : ''}
  `;
}

export function renderPhaseModeSummary(phase) {
  return `<div class="small">当前模式：${phase.mode || 'normal'}</div>`;
}

export function renderIdsHint(phase) {
  return `<div class="tiny">parallel: ${(phase.parallelPhaseIds || []).join(', ') || '-'} | choice: ${(phase.choicePhaseIds || []).join(', ') || '-'} | transitions: ${(phase.transitions || []).length}</div>`;
}
