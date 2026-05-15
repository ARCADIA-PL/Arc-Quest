import {createObjective, createPhase, createQuestSkeleton, createSplash,
    createNpcSkeleton, createDialogueSkeleton} from './factories.js';

export const createBlankQuest = () => {
    const quest = createQuestSkeleton();
    quest.id = 'new_quest';
    quest.title = 'arc_quest.quest.new_quest.title';
    quest.description = 'arc_quest.quest.new_quest.description';
    quest.visualConfig.splashes = [
        createSplash('QUEST_ACQUIRED'),
        createSplash('QUEST_COMPLETED')
    ];

    const phase = createPhase(0);
    phase.id = 'phase_1';
    phase.title = 'arc_quest.phase.new_quest.phase_1.title';
    phase.description = 'arc_quest.phase.new_quest.phase_1.description';

    const objective = createObjective(0);
    objective.id = 'objective_1';
    objective.text = 'arc_quest.objective.new_quest.phase_1.1';

    phase.objectives = [objective];
    quest.phases = [phase];

    return quest;
};

export const state = {
    mode: 'quest',
    registry: {
        quests: {},
        dialogues: {},
        npcs: {},
        npcBindings: {
            dialogue: {},
            npc: {}
        }
    },
    quest: {
        q: createBlankQuest(),
        meta: {file: 'new_quest.json', dirty: false},
        ui: {
            sel: {t: 'quest'},
            tab: 'preview',
            graphView: {x: 0, y: 0, k: 1},
            graphExpanded: false,
            collectionView: 'card',
            paneSizes: {left: 280, center: null, right: 420}
        },
        diag: []
    },
    npc: {
        q: createNpcSkeleton(),
        meta: {file: 'new_npc.json', dirty: false},
        diag: []
    },
    dialogue: {
        q: createDialogueSkeleton(),
        meta: {file: 'new_dialogue.json', dirty: false},
        ui: { selNodeId: '' },
        diag: []
    }
};
