import { setQuestRootField } from './quest-shape-core.js';
import { setCollectionField } from './quest-shape-collection.js';
import { setObjectiveField, setPhaseField, setRewardField } from './quest-shape-phase.js';

export { ensureQuestShape } from './quest-shape-core.js';

export function setByPath(target, bind, value, inputType) {
  const rootResult = setQuestRootField(target, bind, value, inputType);
  if (rootResult !== undefined) return rootResult;

  if (setCollectionField(target, bind, value, inputType)) return;

  if (bind.startsWith('sp.')) {
    const [, i, k] = bind.split('.');
    target.visualConfig.splashes[+i][k] = inputType === 'number' ? Number(value || 0) : value;
    return;
  }

  if (setPhaseField(target, bind, value, inputType)) return;
  if (setObjectiveField(target, bind, value, inputType)) return;
  if (setRewardField(target, bind, value, inputType)) return;
}
