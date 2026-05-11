export function renderPhaseCollectionSection(s, p, field, area) {
    return `
    <h4>Collection Entry Config</h4>
    <div class="card">
      <div class="row">
        ${field('categoryId', `ph.${s.pi}.cec.categoryId`, p.collectionEntryConfig?.categoryId || '')}
        <div class="f"><label>visibilityMode</label><select data-b="ph.${s.pi}.cec.visibilityMode"><option value="VISIBLE_BY_DEFAULT" ${(p.collectionEntryConfig?.visibilityMode || 'VISIBLE_BY_DEFAULT') === 'VISIBLE_BY_DEFAULT' ? 'selected' : ''}>VISIBLE_BY_DEFAULT</option><option value="HIDDEN_BY_DEFAULT" ${p.collectionEntryConfig?.visibilityMode === 'HIDDEN_BY_DEFAULT' ? 'selected' : ''}>HIDDEN_BY_DEFAULT</option><option value="LOCKED" ${p.collectionEntryConfig?.visibilityMode === 'LOCKED' ? 'selected' : ''}>LOCKED</option></select></div>
      </div>
      <div class="row">
        <div class="f"><label>hiddenPresentationMode</label><select data-b="ph.${s.pi}.cec.hiddenPresentationMode"><option value="FULLY_HIDDEN" ${(p.collectionEntryConfig?.hiddenPresentationMode || 'FULLY_HIDDEN') === 'FULLY_HIDDEN' ? 'selected' : ''}>FULLY_HIDDEN</option><option value="PLACEHOLDER" ${p.collectionEntryConfig?.hiddenPresentationMode === 'PLACEHOLDER' ? 'selected' : ''}>PLACEHOLDER</option></select></div>
        <div class="f"><label>countingMode</label><select data-b="ph.${s.pi}.cec.countingMode"><option value="BINARY" ${(p.collectionEntryConfig?.countingMode || 'BINARY') === 'BINARY' ? 'selected' : ''}>BINARY</option><option value="ACCUMULATE" ${p.collectionEntryConfig?.countingMode === 'ACCUMULATE' ? 'selected' : ''}>ACCUMULATE</option><option value="UNIQUE_SET" ${p.collectionEntryConfig?.countingMode === 'UNIQUE_SET' ? 'selected' : ''}>UNIQUE_SET</option></select></div>
      </div>
      <div class="row">
        ${field('completionTarget', `ph.${s.pi}.cec.completionTarget`, p.collectionEntryConfig?.completionTarget ?? 1, 'number')}
        ${field('maxCount', `ph.${s.pi}.cec.maxCount`, p.collectionEntryConfig?.maxCount ?? 1, 'number')}
      </div>
      <div class="row">
        <div class="f"><label>rewardGrantMode</label><select data-b="ph.${s.pi}.cec.rewardGrantMode"><option value="AUTO" ${(p.collectionEntryConfig?.rewardGrantMode || 'AUTO') === 'AUTO' ? 'selected' : ''}>AUTO</option><option value="MANUAL" ${p.collectionEntryConfig?.rewardGrantMode === 'MANUAL' ? 'selected' : ''}>MANUAL</option></select></div>
        ${field('sortOrder', `ph.${s.pi}.cec.sortOrder`, p.collectionEntryConfig?.sortOrder ?? s.pi, 'number')}
      </div>
      <div class="row">
        <div class="f"><label>showInTrackerByDefault</label><select data-b="ph.${s.pi}.cec.showInTrackerByDefault"><option value="true" ${p.collectionEntryConfig?.showInTrackerByDefault ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.showInTrackerByDefault ? 'selected' : ''}>false</option></select></div>
        <div class="f"><label>repeatableProgress</label><select data-b="ph.${s.pi}.cec.repeatableProgress"><option value="true" ${p.collectionEntryConfig?.repeatableProgress ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.repeatableProgress ? 'selected' : ''}>false</option></select></div>
      </div>
      <div class="row">
        <div class="f"><label>repeatableCompletion</label><select data-b="ph.${s.pi}.cec.repeatableCompletion"><option value="true" ${p.collectionEntryConfig?.repeatableCompletion ? 'selected' : ''}>true</option><option value="false" ${!p.collectionEntryConfig?.repeatableCompletion ? 'selected' : ''}>false</option></select></div>
        <div class="f"></div>
      </div>
      ${area('visibilityConditions JSON', `ph.${s.pi}.cec.visibilityConditions`, p.collectionEntryConfig?.visibilityConditions ? JSON.stringify(p.collectionEntryConfig.visibilityConditions, null, 2) : '')}
      ${area('rewardNodes JSON', `ph.${s.pi}.cec.rewardNodes`, p.collectionEntryConfig?.rewardNodes ? JSON.stringify(p.collectionEntryConfig.rewardNodes, null, 2) : '')}
    </div>
  `;
}
