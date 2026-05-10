export function bindConditionEditorClicks(state, descriptor, action) {
  if (action === 'append') {
    return appendConditionBranch(state.q, descriptor);
  }
  if (action === 'delete') {
    return deleteConditionBranch(state.q, descriptor);
  }
  return false;
}

function cloneConditionNode(node) {
  return JSON.parse(JSON.stringify(node || { type: 'always' }));
}

function getConditionNodeByBind(root, bindBase) {
  const parts = bindBase.split('.');
  if (parts[0] === 'q' && parts[1] === 'uc') {
    let cursor = root.unlockConditions?.[Number(parts[2])];
    for (let i = 3; i < parts.length; i++) cursor = cursor?.[parts[i]];
    return cursor;
  }
  if (parts[0] === 'ph') {
    const phase = root.phases?.[Number(parts[1])];
    if (!phase) return null;
    if (parts[2] === 'ec') {
      let cursor = phase.rawEnterCondition;
      for (let i = 3; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
    if (parts[2] === 'tr') {
      let cursor = phase.transitions?.[Number(parts[3])]?.condition;
      for (let i = 5; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
    if (parts[2] === 'ch') {
      let cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
      for (let i = 5; i < parts.length; i++) cursor = cursor?.[parts[i]];
      return cursor;
    }
  }
  return null;
}

function getConditionParentRef(root, bindBase) {
  const parts = bindBase.split('.');
  if (parts[0] === 'q' && parts[1] === 'uc') {
    if (parts.length <= 3) return null;
    let cursor = root.unlockConditions?.[Number(parts[2])];
    for (let i = 3; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
    return { parent: cursor, key: parts[parts.length - 1] };
  }
  if (parts[0] === 'ph') {
    const phase = root.phases?.[Number(parts[1])];
    if (!phase) return null;
    if (parts[2] === 'ec') {
      if (parts.length <= 3) return null;
      let cursor = phase.rawEnterCondition;
      for (let i = 3; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
    if (parts[2] === 'tr') {
      if (parts.length <= 5) return null;
      let cursor = phase.transitions?.[Number(parts[3])]?.condition;
      for (let i = 5; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
    if (parts[2] === 'ch') {
      if (parts.length <= 5) return null;
      let cursor = phase.choices?.[Number(parts[3])]?.visibleCondition;
      for (let i = 5; i < parts.length - 1; i++) cursor = cursor?.[parts[i]];
      return { parent: cursor, key: parts[parts.length - 1] };
    }
  }
  return null;
}

function appendConditionBranch(root, descriptor) {
  const [bindBase, side] = String(descriptor || '').split(':');
  if (!bindBase || !side) return false;
  const node = getConditionNodeByBind(root, bindBase);
  if (!node || (node.type !== 'and' && node.type !== 'or')) return false;
  const existing = cloneConditionNode(node[side] || { type: 'always' });
  node[side] = { type: node.type, left: existing, right: { type: 'always' } };
  return true;
}

function deleteConditionBranch(root, bindBase) {
  const ref = getConditionParentRef(root, bindBase);
  if (!ref?.parent || !ref.key) return false;
  const parent = ref.parent;
  const key = ref.key;
  const siblingKey = key === 'left' ? 'right' : key === 'right' ? 'left' : null;

  if (parent.type === 'not' && key === 'left') {
    parent.left = { type: 'always' };
    return true;
  }

  if ((parent.type === 'and' || parent.type === 'or') && siblingKey) {
    const sibling = cloneConditionNode(parent[siblingKey] || { type: 'always' });
    Object.keys(parent).forEach(prop => delete parent[prop]);
    Object.assign(parent, sibling);
    return true;
  }

  parent[key] = { type: 'always' };
  return true;
}
