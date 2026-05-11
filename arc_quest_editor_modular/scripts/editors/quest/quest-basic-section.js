import {chipEditor} from '../chip-editor.js';
import {boolSelect, modeSelect} from '../quest-editor-sections.js';

export function renderQuestInfoSection(q, field, area) {
    return `
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
      <div class="row">
        ${chipEditor('标签池 (Tags)', q.tags || [], 'q.tags', 'q.tags', '输入 tag 后点击添加')}
        ${chipEditor('接取时触发标记 (setFlagOnAccept)', q.flagsToSetOnAccept || [], 'q.flagsToSetOnAccept', 'q.flagsToSetOnAccept', '输入 flag 后点击添加')}
      </div>
    </div>
  `;
}
