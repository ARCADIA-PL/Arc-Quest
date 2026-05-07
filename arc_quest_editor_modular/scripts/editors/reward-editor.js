function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function renderRewardFields(reward, bindBase, field) {
  if ((reward.type || 'item') === 'var_add') {
    return `
      <div class="row">
        ${field('Variable', `${bindBase}.variable`, reward.variable || '')}
        ${field('Value', `${bindBase}.value`, reward.value ?? 0, 'number')}
      </div>
    `;
  }
  return `
    <div class="row">
      ${field('Item ID', `${bindBase}.itemId`, reward.itemId || 'minecraft:iron_ingot')}
      ${field('Count', `${bindBase}.count`, reward.count ?? 1, 'number')}
    </div>
  `;
}

function rewardBindBase(scope, phaseIndex, rewardIndex) {
  return scope === 'quest' ? `rw.quest.${rewardIndex}` : `rw.phase.${phaseIndex}.${rewardIndex}`;
}

export function renderRewardList(list, scope, phaseIndex, field) {
  return list.map((r, i) => {
    const bindBase = rewardBindBase(scope, phaseIndex, i);
    return `
      <div class="card">
        <div class="row">
          ${enumSelect('Type', `${bindBase}.type`, r.type || 'item', ['item', 'var_add'])}
        </div>
        ${renderRewardFields(r, bindBase, field)}
        <div class="actions"><button data-dr="${scope}:${phaseIndex ?? ''}:${i}">删除</button></div>
      </div>
    `;
  }).join('');
}
