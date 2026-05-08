function enumSelect(label, bind, value, options) {
  return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

function suggestInput(label, bind, value, suggestions, listId) {
  const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
  return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${value || ''}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

function renderItemReward(reward, bindBase) {
  return `
    <div class="row">
      ${suggestInput('Item ID', `${bindBase}.itemId`, reward.itemId || 'minecraft:iron_ingot', ['minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:diamond', 'minecraft:emerald'], `${bindBase}-item`)}
      <div class="f"><label>Count</label><input data-b="${bindBase}.count" type="number" value="${reward.count ?? 1}"></div>
    </div>
  `;
}

function renderVarAddReward(reward, bindBase) {
  return `
    <div class="row">
      <div class="f"><label>Variable</label><input data-b="${bindBase}.variable" value="${reward.variable || ''}" placeholder="quest.progress"></div>
      <div class="f"><label>Value</label><input data-b="${bindBase}.value" type="number" value="${reward.value ?? 0}"></div>
    </div>
  `;
}

function renderCommandReward(reward, bindBase) {
  return `<div class="f"><label>Command</label><textarea data-b="${bindBase}.command" placeholder="/give @p minecraft:diamond">${reward.command || ''}</textarea></div>`;
}

function renderFlagReward(reward, bindBase) {
  return `
    <div class="row">
      <div class="f"><label>Flag</label><input data-b="${bindBase}.flag" value="${reward.flag || ''}" placeholder="arc_quest:chapter_1_done"></div>
      <div class="f"><label>Enabled</label><select data-b="${bindBase}.enabled"><option value="true" ${reward.enabled !== false ? 'selected' : ''}>true</option><option value="false" ${reward.enabled === false ? 'selected' : ''}>false</option></select></div>
    </div>
  `;
}

function renderCurrencyReward(reward, bindBase) {
  return `
    <div class="row">
      <div class="f"><label>Currency</label><input data-b="${bindBase}.currencyId" value="${reward.currencyId || 'arc_quest:coin'}" placeholder="arc_quest:coin"></div>
      <div class="f"><label>Amount</label><input data-b="${bindBase}.amount" type="number" value="${reward.amount ?? 1}"></div>
    </div>
  `;
}

function renderRewardFields(reward, bindBase) {
  const type = reward.type || 'item';
  if (type === 'var_add') return renderVarAddReward(reward, bindBase);
  if (type === 'command') return renderCommandReward(reward, bindBase);
  if (type === 'flag') return renderFlagReward(reward, bindBase);
  if (type === 'currency') return renderCurrencyReward(reward, bindBase);
  return renderItemReward(reward, bindBase);
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
          ${enumSelect('Type', `${bindBase}.type`, r.type || 'item', ['item', 'var_add', 'command', 'flag', 'currency'])}
        </div>
        ${renderRewardFields(r, bindBase, field)}
        <div class="actions"><button data-dr="${scope}:${phaseIndex ?? ''}:${i}">删除</button></div>
      </div>
    `;
  }).join('');
}
