function boolSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="false" ${!value ? 'selected' : ''}>False</option><option value="true" ${value ? 'selected' : ''}>True</option></select></div>`;
}

function enumSelect(label, bind, value, options) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}">${options.map(option => `<option value="${option}" ${value === option ? 'selected' : ''}>${option}</option>`).join('')}</select></div>`;
}

// ... 保持 renderCompletionRulesEditor, renderTopLevelRewardItemsEditor 等函数的内部逻辑不变 ...
// 为了篇幅限制，这里只展示顶层 renderQuestEditor 的重构排版（子函数只需复用即可，因为 CSS 已经重写了 .card 和 .row）

// --- 省略其他内部 helper 函数 (直接拷贝你原有的内部函数即可，无需修改逻辑) ---
// *注意*: 在实际覆盖时，请将你原有的 helper 函数 (renderCompletionRulesEditor 等) 放在这里。
// 下面只提供修改过的 renderQuestEditor 主入口。

function modeSelect(label, bind, value) {
    return `<div class="f"><label>${label}</label><select data-b="${bind}"><option value="translatable" ${value === 'translatable' ? 'selected' : ''}>Translatable</option><option value="literal" ${value === 'literal' ? 'selected' : ''}>Literal</option></select></div>`;
}

export function renderQuestEditor(state, field, area) {
    const q = state.q;
    // 此处我将原来的零散表单包裹在不同主题的 .card 中，实现 Apple 风格的“分组表单”
    return `
    <div class="sec">
      <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin-bottom: 20px;">架构参数设定 (Quest Info)</h3>
      
      <div class="card">
        ${field('任务唯一标识 (Quest ID)', 'q.id', q.id)}
        <div class="row">
          ${field('标题键值 (Title Key)', 'q.title', q.title)}
          ${modeSelect('文本模式 (Title Mode)', 'q.titleMode', q.titleMode || 'translatable')}
        </div>
        <div class="row">
          ${area('描述键值 (Description Key)', 'q.description', q.description)}
          ${modeSelect('文本模式 (Desc Mode)', 'q.descriptionMode', q.descriptionMode || 'translatable')}
        </div>
        <div class="row">
          ${field('排序权重 (Sort Order)', 'q.sortOrder', q.sortOrder, 'number')}
          ${boolSelect('允许重复执行 (Repeatable)', 'q.repeatable', q.repeatable)}
        </div>
        ${field('标签池 (Tags, 逗号分隔)', 'q.tags', q.tags.join(', '))}
      </div>

      <h4>Datapack 顶层设置 (Top-level Specs)</h4>
      <div class="card">
        <div class="row">
          ${field('分类 (Category)', 'q.category', q.category || '')}
          ${field('模式 (Mode)', 'q.mode', q.mode || '')}
        </div>
        <div class="row">
           ${field('初始阶段ID (Initial Phase ID)', 'q.initialPhaseId', q.initialPhaseId || '')}
           ${field('图标路径 (Icon Texture)', 'q.iconTexture', q.iconTexture || '')}
        </div>
        <div class="row">
          ${field('商店ID (Chapter Shop ID)', 'q.chapterShopId', q.chapterShopId || '')}
          ${field('商店类型 (Chapter Shop Type)', 'q.chapterShopType', q.chapterShopType || '')}
        </div>
        <div class="row">
          ${boolSelect('商店常驻 (Persistent)', 'q.chapterShopPersistent', q.chapterShopPersistent)}
          ${field('完成时触发标记 (flagsToSetOnComplete)', 'q.flagsToSetOnComplete', (q.flagsToSetOnComplete || []).join(', '))}
        </div>
      </div>
      
      <div class="actions" style="margin-top: 24px;">
        <button id="addPhaseBtn" class="primary">
           <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M12 5v14M5 12h14"/></svg> 新增执行阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}