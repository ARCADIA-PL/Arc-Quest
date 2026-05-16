import {esc} from '../core/utils.js';
import {renderConditionTree} from './condition-editor.js';

function suggestInput(label, bind, value, suggestions, listId) {
    const opts = (suggestions || []).map(v => `<option value="${v}"></option>`).join('');
    return `<div class="f"><label>${label}</label><input data-b="${bind}" list="${listId}" value="${esc(value || '')}" placeholder="${label}"><datalist id="${listId}">${opts}</datalist></div>`;
}

export function renderNpcBindingEditor(npc, bi, registry) {
    const b = npc.bindings?.[bi];
    if (!b) return renderNpcOverviewFallback(npc);

    const dialogueSuggestions = registry?.dialogues ? Object.keys(registry.dialogues) : [];

    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-npc-nav="overview" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 返回</button>
        <span class="breadcrumb">
          <span data-npc-nav="overview" class="breadcrumb-link">⚙ NPC 配置</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">绑定 #${bi + 1} (${esc(b.bindingId || '未命名')})</span>
        </span>
      </div>
      <div class="row">
        <div class="f"><label>绑定 ID</label><input data-b="npc.bind.${bi}.bindingId" value="${esc(b.bindingId || '')}" placeholder="binding_1"></div>
        <div class="f"><label>优先级</label><input type="number" data-b="npc.bind.${bi}.priority" value="${b.priority ?? 0}" min="0" max="100"></div>
      </div>
      <div class="row">
        ${suggestInput('对话 ID', `npc.bind.${bi}.dialogueId`, b.dialogueId || '', dialogueSuggestions, `npc-bind-${bi}-dialogue-list`)}
      </div>
      <div class="row">
        <div class="f"><label>NBT 对话 Key</label><input data-b="npc.bind.${bi}.dialogueIdFromNbt" value="${esc(b.dialogueIdFromNbt || '')}" placeholder="ArcQuestDialogueId"></div>
      </div>
      <div style="margin-top:8px">
        <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
        ${renderConditionTree(`npc.bind.${bi}.condition`, b.condition || {condition: 'arc_quest:always'}, registry, true)}
        <div class="actions"><button data-cond-append="npc.bind.${bi}.condition">＋ 添加条件</button></div>
      </div>
      <div class="actions" style="margin-top:12px">
        <button data-dnpcbind="${bi}">删除此绑定</button>
      </div>
    </div>
  `;
}

function renderNpcOverviewFallback(npc) {
    return `<div class="sec">
      <div class="breadcrumb"><span class="breadcrumb-current">⚙ NPC 配置</span></div>
      <div class="small" style="color:var(--text-mut)">绑定不存在，请返回概览</div>
      <div style="margin-top:8px"><button data-npc-nav="overview" class="toolbar-btn small-btn">← 返回概览</button></div>
    </div>`;
}
