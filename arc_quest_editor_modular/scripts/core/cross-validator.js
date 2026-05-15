export function validateCrossReferences(registry, currentQuest) {
    const issues = [];

    validateNpcToDialogue(registry, issues);
    validateDialogueToQuest(registry, issues);
    validateDialogueNodes(registry, issues);
    if (currentQuest) {
        validateCurrentQuestConditions(registry, currentQuest, issues);
    }
    validateRegistryIntegrity(registry, issues);

    const errCount = issues.filter(i => i.lvl === 'err').length;
    const warnCount = issues.filter(i => i.lvl === 'warn').length;
    const infoCount = issues.filter(i => i.lvl === 'info').length;

    return {
        issues,
        summary: {
            questCount: Object.keys(registry.quests || {}).length,
            dialogueCount: Object.keys(registry.dialogues || {}).length,
            npcCount: Object.keys(registry.npcs || {}).length,
            totalErrors: errCount,
            totalWarnings: warnCount,
            totalInfos: infoCount,
        }
    };
}

function validateNpcToDialogue(registry, issues) {
    const npcs = registry.npcs || {};
    const dialogues = registry.dialogues || {};

    for (const [entityType, npc] of Object.entries(npcs)) {
        for (let bi = 0; bi < (npc.bindings || []).length; bi++) {
            const binding = npc.bindings[bi];
            if (binding.dialogueId && !dialogues[binding.dialogueId]) {
                issues.push({
                    lvl: 'err',
                    src: {type: 'NPC', id: entityType},
                    path: `bindings[${bi}].dialogueId`,
                    msg: `对话 "${binding.dialogueId}" 未在注册表中找到`
                });
            }
        }
    }
}

function walkConditions(conditions, parentPath, issues, registry, dialogueId) {
    if (!conditions) return;
    const quests = registry.quests || {};

    for (let ci = 0; ci < conditions.length; ci++) {
        const c = conditions[ci];
        const path = `${parentPath}.conditions[${ci}]`;

        if (c.questId && !quests[c.questId]) {
            issues.push({
                lvl: 'err',
                src: {type: 'Dialogue', id: dialogueId},
                path,
                msg: `任务 "${c.questId}" 未在注册表中找到`
            });
        }
        if (c.questId && c.phaseId && quests[c.questId]) {
            const questEntry = quests[c.questId];
            const phaseIds = (questEntry.phases || []).map(p => p.id);
            if (!phaseIds.includes(c.phaseId)) {
                issues.push({
                    lvl: 'warn',
                    src: {type: 'Dialogue', id: dialogueId},
                    path,
                    msg: `阶段 "${c.phaseId}" 不在任务 "${c.questId}" 的阶段列表中`
                });
            }
        }

        if (c.inner) {
            walkConditions([c.inner], `${path}.inner`, issues, registry, dialogueId);
        }
        if (c.conditions) {
            walkConditions(c.conditions, path, issues, registry, dialogueId);
        }
    }
}

function validateDialogueToQuest(registry, issues) {
    const dialogues = registry.dialogues || {};

    for (const [dialogueId, dialogue] of Object.entries(dialogues)) {
        for (const node of (dialogue.nodes || [])) {
            const condTexts = node.conditionalTexts || {};
            for (const [sayId, condSay] of Object.entries(condTexts)) {
                walkConditions(
                    condSay.conditions || [],
                    `nodes[${node.nodeId}].conditionalTexts[${sayId}]`,
                    issues,
                    registry,
                    dialogueId
                );
            }

            for (let chi = 0; chi < (node.choices || []).length; chi++) {
                const choice = node.choices[chi];
                const choicePath = `nodes[${node.nodeId}].choices[${chi}]`;
                walkConditions(
                    choice.conditions || [],
                    choicePath,
                    issues,
                    registry,
                    dialogueId
                );

                for (let ai = 0; ai < (choice.actions || []).length; ai++) {
                    const action = choice.actions[ai];
                    if (action.type === 'start_quest' && action.questId) {
                        if (!registry.quests || !registry.quests[action.questId]) {
                            issues.push({
                                lvl: 'err',
                                src: {type: 'Dialogue', id: dialogueId},
                                path: `${choicePath}.actions[${ai}].questId`,
                                msg: `start_quest 引用的任务 "${action.questId}" 未在注册表中找到`
                            });
                        }
                    }
                }
            }
        }
    }
}

function validateDialogueNodes(registry, issues) {
    const dialogues = registry.dialogues || {};

    for (const [dialogueId, dialogue] of Object.entries(dialogues)) {
        const nodeIds = new Set((dialogue.nodes || []).map(n => n.nodeId).filter(Boolean));

        if (dialogue.startNodeId && !nodeIds.has(dialogue.startNodeId)) {
            issues.push({
                lvl: 'err',
                src: {type: 'Dialogue', id: dialogueId},
                path: 'startNodeId',
                msg: `起始节点 "${dialogue.startNodeId}" 不在节点列表中`
            });
        }

        for (const node of (dialogue.nodes || [])) {
            for (let chi = 0; chi < (node.choices || []).length; chi++) {
                const choice = node.choices[chi];
                if (choice.nextNodeId && !nodeIds.has(choice.nextNodeId)) {
                    issues.push({
                        lvl: 'warn',
                        src: {type: 'Dialogue', id: dialogueId},
                        path: `nodes[${node.nodeId}].choices[${chi}].nextNodeId`,
                        msg: `目标节点 "${choice.nextNodeId}" 不在对话节点列表中`
                    });
                }
            }

            if (node.autoNextId && !nodeIds.has(node.autoNextId)) {
                issues.push({
                    lvl: 'warn',
                    src: {type: 'Dialogue', id: dialogueId},
                    path: `nodes[${node.nodeId}].autoNextId`,
                    msg: `自动下一节点 "${node.autoNextId}" 不在对话节点列表中`
                });
            }
        }
    }
}

function walkCurrentQuestConditions(condition, path, issues, registry, questId) {
    if (!condition) return;
    const quests = registry.quests || {};

    if (condition.questId && condition.questId !== questId && !quests[condition.questId]) {
        issues.push({
            lvl: 'warn',
            src: {type: 'Quest', id: questId},
            path,
            msg: `条件引用任务 "${condition.questId}" 未在注册表中找到`
        });
    }
    if (condition.questId && condition.phaseId && quests[condition.questId]) {
        const questEntry = quests[condition.questId];
        const phaseIds = (questEntry.phases || []).map(p => p.id);
        if (!phaseIds.includes(condition.phaseId)) {
            issues.push({
                lvl: 'warn',
                src: {type: 'Quest', id: questId},
                path,
                msg: `条件中阶段 "${condition.phaseId}" 不在任务 "${condition.questId}" 的阶段列表中`
            });
        }
    }

    if (condition.inner) {
        walkCurrentQuestConditions(condition.inner, `${path}.inner`, issues, registry, questId);
    }
    if (condition.conditions) {
        for (let i = 0; i < condition.conditions.length; i++) {
            walkCurrentQuestConditions(condition.conditions[i], `${path}.conditions[${i}]`, issues, registry, questId);
        }
    }
}

function validateCurrentQuestConditions(registry, currentQuest, issues) {
    const questId = currentQuest.id || 'current';

    for (const uc of (currentQuest.unlockConditions || [])) {
        walkCurrentQuestConditions(uc, 'unlockConditions', issues, registry, questId);
    }

    for (let pi = 0; pi < (currentQuest.phases || []).length; pi++) {
        const phase = currentQuest.phases[pi];
        const phasePath = `phases[${pi}]`;

        if (phase.enterCondition) {
            walkCurrentQuestConditions(phase.enterCondition, `${phasePath}.enterCondition`, issues, registry, questId);
        }

        for (let ti = 0; ti < (phase.transitions || []).length; ti++) {
            const tr = phase.transitions[ti];
            if (tr.condition) {
                walkCurrentQuestConditions(tr.condition, `${phasePath}.transitions[${ti}].condition`, issues, registry, questId);
            }
        }

        for (let ci = 0; ci < (phase.choices || []).length; ci++) {
            const ch = phase.choices[ci];
            if (ch.visibleCondition) {
                walkCurrentQuestConditions(ch.visibleCondition, `${phasePath}.choices[${ci}].visibleCondition`, issues, registry, questId);
            }
        }
    }
}

function validateRegistryIntegrity(registry, issues) {
    const questCount = Object.keys(registry.quests || {}).length;
    const dialogueCount = Object.keys(registry.dialogues || {}).length;
    const npcCount = Object.keys(registry.npcs || {}).length;

    issues.push({
        lvl: 'info',
        src: {type: 'Registry', id: '-'},
        path: 'summary',
        msg: `注册表: ${questCount} 任务, ${dialogueCount} 对话, ${npcCount} NPC`
    });

    if (questCount === 0 && dialogueCount === 0 && npcCount === 0) {
        issues.push({
            lvl: 'info',
            src: {type: 'Registry', id: '-'},
            path: 'summary',
            msg: '注册表为空，请先导入 JSON 文件到库'
        });
    }
}
