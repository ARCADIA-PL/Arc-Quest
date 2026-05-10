import { renderRewardList } from '../../editors/reward-editor.js';

export function renderRewardsView(state, field) {
  return `<div class="sec"><h3>Global Rewards</h3><div class="card">${renderRewardList(state.q.rewards, 'quest', null, field)}<div class="actions"><button class="primary" id="addQuestRewardBtn">+ 添加全局奖励</button></div></div></div>`;
}
