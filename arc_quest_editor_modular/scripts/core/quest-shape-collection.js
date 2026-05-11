import {setLooseJson} from './quest-shape-core.js';

function setTopCollectionConfigField(target, fieldName, value) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    if (fieldName === 'allowCategoryCollapse' || fieldName === 'showCompletedEntries' || fieldName === 'showProgressInTracker') {
        target.collectionConfig[fieldName] = value === 'true';
        return;
    }
    target.collectionConfig[fieldName] = value;
}

function setTopCompletionRuleField(target, ruleIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.completionRules ||= [];
    target.collectionConfig.completionRules[ruleIndex] ||= {type: 'completed_entry_count', value: 1};
    target.collectionConfig.completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeField(target, rewardNodeIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.rewardNodes ||= [];
    target.collectionConfig.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'QUEST',
        grantMode: 'MANUAL',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    target.collectionConfig.rewardNodes[rewardNodeIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeCompletionRuleField(target, rewardNodeIndex, ruleIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.rewardNodes ||= [];
    target.collectionConfig.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'QUEST',
        grantMode: 'MANUAL',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules ||= [];
    target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules[ruleIndex] ||= {
        type: 'completed_entry_count',
        value: 1
    };
    target.collectionConfig.rewardNodes[rewardNodeIndex].completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setTopRewardNodeRewardField(target, rewardNodeIndex, rewardIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.rewardNodes ||= [];
    target.collectionConfig.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'QUEST',
        grantMode: 'MANUAL',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    target.collectionConfig.rewardNodes[rewardNodeIndex].rewards ||= [];
    target.collectionConfig.rewardNodes[rewardNodeIndex].rewards[rewardIndex] ||= {type: 'item', itemId: '', count: 1};
    target.collectionConfig.rewardNodes[rewardNodeIndex].rewards[rewardIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setCategoryField(target, categoryIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.categories ||= [];
    target.collectionConfig.categories[categoryIndex] ||= {
        categoryId: '',
        displayName: {mode: 'translatable', value: ''},
        sortOrder: categoryIndex,
        completionRules: [],
        rewardNodes: []
    };
    const cat = target.collectionConfig.categories[categoryIndex];
    if (fieldName === 'displayMode') {
        cat.displayName ||= {mode: 'translatable', value: ''};
        cat.displayName.mode = value;
        return;
    }
    if (fieldName === 'displayValue') {
        cat.displayName ||= {mode: 'translatable', value: ''};
        cat.displayName.value = value;
        return;
    }
    cat[fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeField(target, categoryIndex, rewardNodeIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.categories ||= [];
    target.collectionConfig.categories[categoryIndex] ||= {
        categoryId: '',
        displayName: {mode: 'translatable', value: ''},
        sortOrder: categoryIndex,
        completionRules: [],
        rewardNodes: []
    };
    const cat = target.collectionConfig.categories[categoryIndex];
    cat.rewardNodes ||= [];
    cat.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'CATEGORY',
        grantMode: 'AUTO',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    cat.rewardNodes[rewardNodeIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeRewardField(target, categoryIndex, rewardNodeIndex, rewardIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.categories ||= [];
    target.collectionConfig.categories[categoryIndex] ||= {
        categoryId: '',
        displayName: {mode: 'translatable', value: ''},
        sortOrder: categoryIndex,
        completionRules: [],
        rewardNodes: []
    };
    const cat = target.collectionConfig.categories[categoryIndex];
    cat.rewardNodes ||= [];
    cat.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'CATEGORY',
        grantMode: 'AUTO',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    cat.rewardNodes[rewardNodeIndex].rewards ||= [];
    cat.rewardNodes[rewardNodeIndex].rewards[rewardIndex] ||= {type: 'item', itemId: '', count: 1};
    cat.rewardNodes[rewardNodeIndex].rewards[rewardIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setCategoryCompletionRuleField(target, categoryIndex, ruleIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.categories ||= [];
    target.collectionConfig.categories[categoryIndex] ||= {
        categoryId: '',
        displayName: {mode: 'translatable', value: ''},
        sortOrder: categoryIndex,
        completionRules: [],
        rewardNodes: []
    };
    const cat = target.collectionConfig.categories[categoryIndex];
    cat.completionRules ||= [];
    cat.completionRules[ruleIndex] ||= {type: 'completed_entry_count', value: 1};
    cat.completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

function setRewardNodeCompletionRuleField(target, categoryIndex, rewardNodeIndex, ruleIndex, fieldName, value, inputType) {
    target.collectionConfig ||= {categories: [], rewardNodes: [], completionRules: []};
    target.collectionConfig.categories ||= [];
    target.collectionConfig.categories[categoryIndex] ||= {
        categoryId: '',
        displayName: {mode: 'translatable', value: ''},
        sortOrder: categoryIndex,
        completionRules: [],
        rewardNodes: []
    };
    const cat = target.collectionConfig.categories[categoryIndex];
    cat.rewardNodes ||= [];
    cat.rewardNodes[rewardNodeIndex] ||= {
        nodeId: '',
        scope: 'CATEGORY',
        grantMode: 'AUTO',
        rewards: [],
        completionRules: [],
        scopeRefId: ''
    };
    cat.rewardNodes[rewardNodeIndex].completionRules ||= [];
    cat.rewardNodes[rewardNodeIndex].completionRules[ruleIndex] ||= {type: 'completed_entry_count', value: 1};
    cat.rewardNodes[rewardNodeIndex].completionRules[ruleIndex][fieldName] = inputType === 'number' ? Number(value || 0) : value;
}

export function setCollectionField(target, bind, value, inputType) {
    if (bind.startsWith('q.cc.')) {
        const [, , fieldName] = bind.split('.');
        setTopCollectionConfigField(target, fieldName, value);
        return true;
    }
    if (bind.startsWith('q.tcr.')) {
        const [, , ruleIndex, fieldName] = bind.split('.');
        setTopCompletionRuleField(target, +ruleIndex, fieldName, value, inputType);
        return true;
    }
    if (bind.startsWith('q.trn.')) {
        const [, , rewardNodeIndex, fieldName, rewardIndex, rewardField] = bind.split('.');
        if (fieldName === 'cr') setTopRewardNodeCompletionRuleField(target, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
        else if (fieldName === 'rw') setTopRewardNodeRewardField(target, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
        else setTopRewardNodeField(target, +rewardNodeIndex, fieldName, value, inputType);
        return true;
    }
    if (bind.startsWith('q.cat.')) {
        const [, , i, fieldName, rewardNodeIndex, rewardNodeField, rewardIndex, rewardField] = bind.split('.');
        if (fieldName === 'cr') setCategoryCompletionRuleField(target, +i, +rewardNodeIndex, rewardNodeField, value, inputType);
        else if (fieldName === 'rn' && rewardNodeField === 'cr') setRewardNodeCompletionRuleField(target, +i, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
        else if (fieldName === 'rn' && rewardNodeField === 'rw') setRewardNodeRewardField(target, +i, +rewardNodeIndex, +rewardIndex, rewardField, value, inputType);
        else if (fieldName === 'rn') setRewardNodeField(target, +i, +rewardNodeIndex, rewardNodeField, value, inputType);
        else setCategoryField(target, +i, fieldName, value, inputType);
        return true;
    }
    return false;
}
