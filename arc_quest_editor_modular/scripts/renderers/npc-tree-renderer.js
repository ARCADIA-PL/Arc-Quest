import {esc} from '../core/utils.js';

export function renderNpcTree(state, leftEl) {
    const npc = state.npc.q;
    const sel = state.npc.ui.sel;
    const bindings = npc.bindings || [];

    const bindingItems = bindings.map((b, i) => {
        const isActive = sel.t === 'binding' && sel.bi === i;
        const dialogueLabel = b.dialogueId || (b.dialogueIdFromNbt ? `NBT:${b.dialogueIdFromNbt}` : '未设置');
        return `<div class="tree-item ${isActive ? 'active' : ''}" data-npc-nav="binding" data-npc-bi="${i}">
          <span>🔗 ${esc(b.bindingId || `#${i + 1}`)}</span>
          <span class="tree-item-hint">${esc(dialogueLabel.substring(0, 24))}</span>
        </div>`;
    }).join('');

    leftEl.innerHTML = `
      <div class="tree-list">
        <div class="tree-header">NPC: ${esc(npc.entityType || '未设置')}</div>
        <div class="tree-item ${sel.t === 'overview' ? 'active' : ''}" data-npc-nav="overview">
          📋 概览
        </div>
        <div class="tree-section">
          <div class="tree-section-header">🔗 绑定 (${bindings.length})</div>
          ${bindingItems || '<div class="tree-item-hint">暂无绑定</div>'}
        </div>
        <div class="tree-item ${sel.t === 'commands' ? 'active' : ''}" data-npc-nav="commands">
          ⚡ 命令
        </div>
      </div>
      <div style="padding:8px">
        <button id="addNpcBindBtn" class="toolbar-btn small-btn" style="width:100%">＋ 添加绑定</button>
      </div>
    `;
}

export function bindNpcTreeSelection(leftEl, state, rerender) {
    leftEl.onclick = e => {
        const nav = e.target.closest('[data-npc-nav]');
        if (!nav) return;
        const t = nav.dataset.npcNav;
        if (t === 'overview') state.npc.ui.sel = {t: 'overview'};
        else if (t === 'binding' && nav.dataset.npcBi !== undefined) {
            state.npc.ui.sel = {t: 'binding', bi: +nav.dataset.npcBi};
        }
        else if (t === 'commands') state.npc.ui.sel = {t: 'commands'};
        rerender();
    };
}
