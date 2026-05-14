export function renderVisualEditor(state, field, area) {
    const v = state.quest.q.visualConfig;
    return `
    <div class="sec">
      <h3>VisualConfig</h3>
      ${field('ThemeColor', 'q.theme', v.themeColor, 'color')}
      <h4>Splashes</h4>
      ${(v.splashes || []).map((x, i) => `<div class="card"><b>${x.eventType || '未命名'}</b>${field('事件类型', `sp.${i}.eventType`, x.eventType || 'QUEST_ACQUIRED')}${field('Texture', `sp.${i}.texture`, x.texture || '')}${field('Scale', `sp.${i}.scale`, x.scale ?? 1, 'number')}<div class="actions"><button data-ds="${i}">删除</button></div></div>`).join('')}
      <div class="actions"><button id="addSplashBtn">+ 新增 Splash</button></div>
      <h4 style="margin-top:14px">Icons</h4>
      ${area('icons JSON', 'q.icons', v.icons ? JSON.stringify(v.icons, null, 2) : '')}
      <div class="tiny">示例：{"quest":{"texture":"arc_quest:textures/gui/quest.png","scale":1}}</div>
    </div>
  `;
}
