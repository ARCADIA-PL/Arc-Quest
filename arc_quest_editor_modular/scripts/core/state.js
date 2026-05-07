export const createBlankQuest = () => ({
  id: 'new_quest',
  title: 'arc_quest.quest.new_quest.title',
  description: 'arc_quest.quest.new_quest.description',
  sortOrder: 0,
  repeatable: false,
  tags: [],
  visualConfig: { themeColor: '#63c7ff', splashes: [] },
  rewards: [],
  phases: [{
    id: 'phase_1', mode: 'normal', title: 'arc_quest.phase.new_quest.phase_1', description: '', autoStart: false,
    parallelPhaseIds: [], choicePhaseIds: [],
    objectives: [{ type: 'kill', id: 'kill_target_1', text: 'arc_quest.objective.new_quest.phase_1.0', count: 1, entityType: 'minecraft:zombie' }],
    rewards: []
  }]
});

export const state = {
  meta: { file: 'new_quest.json', dirty: false },
  q: createBlankQuest(),
  ui: {
    sel: { t: 'quest' },
    tab: 'preview',
    graphView: { x: 0, y: 0, k: 1 },
    graphExpanded: false,
    paneSizes: { left: 280, center: null, right: 380 }
  },
  diag: []
};
