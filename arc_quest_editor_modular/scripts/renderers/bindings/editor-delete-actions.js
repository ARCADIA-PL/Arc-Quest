import { buildReferenceIndex } from '../../core/utils.js';
import { removePhaseReferences, autoGeneratePhaseKeys } from './editor-helpers.js';

export function handleDeleteButtonAction(btn, state) {
  const d = btn.dataset;
  const id = btn.id;
  const s = state.ui.sel;
  const q = state.q;

  if (id === 'deletePhaseBtn') {
    if (q.phases.length <= 1) return { handled: true, mutate: false, message: '至少保留一个 phase。' };
    const removedId = q.phases[s.pi]?.id;
    const refIndex = buildReferenceIndex(q);
    const incoming = refIndex.incoming.get(`phase:${removedId}`) || [];
    const impactLines = incoming.map(r => `- ${r.kind} @ ${r.path} (${r.from} -> ${r.to})`).join('\n');
    const impactTip = incoming.length ? `\n\n检测到 ${incoming.length} 条引用将被清理：\n${impactLines}` : '';
    return {
      handled: true,
      mutate: confirm(`确定删除 phase: ${removedId} 吗？${impactTip}`),
      apply: () => {
        q.phases.splice(s.pi, 1);
        removePhaseReferences(q, removedId);
        state.ui.sel = { t: 'quest' };
      }
    };
  }

  if (d.ds !== undefined) {
    return { handled: true, mutate: true, apply: () => q.visualConfig.splashes.splice(+d.ds, 1) };
  }
  if (d.dtcr !== undefined) {
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.completionRules?.splice(+d.dtcr, 1) };
  }
  if (d.dtrn !== undefined) {
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.rewardNodes?.splice(+d.dtrn, 1) };
  }
  if (d.dtrcr) {
    const [ri, cri] = d.dtrcr.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.rewardNodes?.[+ri]?.completionRules?.splice(+cri, 1) };
  }
  if (d.dtrr) {
    const [ri, rwi] = d.dtrr.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.rewardNodes?.[+ri]?.rewards?.splice(+rwi, 1) };
  }
  if (d.dc !== undefined) {
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.categories?.splice(+d.dc, 1) };
  }
  if (d.dcr) {
    const [ci, cri] = d.dcr.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.categories?.[+ci]?.completionRules?.splice(+cri, 1) };
  }
  if (d.drn) {
    const [ci, ri] = d.drn.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.categories?.[+ci]?.rewardNodes?.splice(+ri, 1) };
  }
  if (d.drcr) {
    const [ci, ri, cri] = d.drcr.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.categories?.[+ci]?.rewardNodes?.[+ri]?.completionRules?.splice(+cri, 1) };
  }
  if (d.drr) {
    const [ci, ri, rwi] = d.drr.split(':');
    return { handled: true, mutate: true, apply: () => q.collectionConfig?.categories?.[+ci]?.rewardNodes?.[+ri]?.rewards?.splice(+rwi, 1) };
  }
  if (d.dr) {
    const [scope, phaseIndex, itemIndex] = d.dr.split(':');
    return { handled: true, mutate: true, apply: () => (scope === 'quest' ? q.rewards : q.phases[+phaseIndex].rewards).splice(+itemIndex, 1) };
  }
  if (d.do !== undefined) {
    return {
      handled: true,
      mutate: true,
      apply: () => {
        q.phases[s.pi].objectives.splice(+d.do, 1);
        autoGeneratePhaseKeys(q, s.pi);
        state.ui.sel = { t: 'phase', pi: s.pi };
      }
    };
  }
  if (d.dt !== undefined) {
    return { handled: true, mutate: true, apply: () => q.phases[s.pi].transitions.splice(+d.dt, 1) };
  }
  if (d.dch !== undefined) {
    return { handled: true, mutate: true, apply: () => q.phases[s.pi].choices.splice(+d.dch, 1) };
  }

  return { handled: false, mutate: false };
}
