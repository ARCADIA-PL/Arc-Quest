export function renderCooldownGroup(bindBase, obj) {
    return `
    <div class="row">
      <div class="f"><label>Repeatable</label>
        <select data-b="${bindBase}.repeatable">
          <option value="true" ${obj.repeatable !== false ? 'selected' : ''}>是</option>
          <option value="false" ${obj.repeatable === false ? 'selected' : ''}>否（仅一次）</option>
        </select>
      </div>
      <div class="f"><label>冷却类型</label>
        <select data-b="${bindBase}.cooldownType">
          <option value="NONE" ${obj.cooldownType === 'NONE' ? 'selected' : ''}>无冷却</option>
          <option value="SECONDS" ${obj.cooldownType === 'SECONDS' ? 'selected' : ''}>秒</option>
          <option value="GAME_DAY" ${obj.cooldownType === 'GAME_DAY' ? 'selected' : ''}>游戏日</option>
          <option value="GAME_TICK" ${obj.cooldownType === 'GAME_TICK' ? 'selected' : ''}>游戏 Tick</option>
        </select>
      </div>
      <div class="f"><label>冷却秒数</label>
        <input type="number" data-b="${bindBase}.cooldownSeconds" value="${obj.cooldownSeconds ?? 0}" min="0" max="86400">
      </div>
      <div class="f"><label>重置 Ticks</label>
        <input type="number" data-b="${bindBase}.resetTimeTicks" value="${obj.resetTimeTicks ?? 0}" min="0" max="24000">
      </div>
    </div>`;
}
