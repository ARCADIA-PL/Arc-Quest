import {esc} from '../core/utils.js';
import {renderTextSpec} from './textspec-editor.js';
import {renderConditionTree} from './condition-editor.js';

export function renderCardStrip({cardsHtml, count, stripPrefix, ni, countText, label}) {
    const leftSvg = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>';
    const rightSvg = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>';
    return `
    <div class="strip-nav">
      <span class="strip-nav-label">${label}</span>
      <div class="strip-nav-cards" data-strip-id="${stripPrefix}-${ni}">${cardsHtml}</div>
      <button class="strip-nav-btn" data-strip-scroll="${stripPrefix}-left" title="滚动到最左">${leftSvg}</button>
      <button class="strip-nav-btn" data-strip-scroll="${stripPrefix}-right" title="滚动到最右">${rightSvg}</button>
      <span class="strip-nav-count">${countText}</span>
    </div>`;
}

export function renderDialogueSayIfEditor(node, key, sayIf, ni, registry, dialogue) {
    const isDefault = key === ':default';
    const sp = isDefault ? `diag.node.${ni}` : `diag.node.${ni}.condText.${key}`;
    const entries = Object.entries(node.conditionalTexts || {});
    const keys = entries.map(([k]) => k);

    const defaultCardHtml = `<span class="strip-card sayif-anchor-strip ${isDefault ? 'on' : ''}" data-jump-sayif="${ni}::default">🟢 默认</span>`;
    const sayIfCardsHtml = defaultCardHtml + entries.map(([k]) => {
        const isActive = !isDefault && k === key;
        return `<span class="strip-card ${isActive ? 'on' : ''}" data-jump-sayif="${ni}:${k}">${esc(k)}</span>`;
    }).join('');

    const choices = node.choices || [];
    const choiceCardsHtml = choices.map((ch, ci) => {
        return `<span class="strip-card" data-jump-choice="${ni}:${ci}">${esc(ch.choiceId || `#${ci + 1}`)}</span>`;
    }).join('');

    const total = entries.length + 1;
    const curIdx = isDefault ? 0 : keys.indexOf(key) + 1;

    return `
    <div class="sec">
      <div class="card-strip-bar">
        <button data-goto-node="${ni}" class="toolbar-btn small-btn" style="font-weight:700;color:var(--accent)">← 节点</button>
        <span class="breadcrumb">
          <span data-goto-config class="breadcrumb-link">⚙ 对话配置</span>
          <span class="breadcrumb-sep">›</span>
          <span data-goto-node="${ni}" class="breadcrumb-link">${esc(node.nodeId || '')}</span>
          <span class="breadcrumb-sep">›</span>
          <span class="breadcrumb-current">${isDefault ? '默认 (default)' : esc(key)}</span>
        </span>
      </div>
      ${renderCardStrip({cardsHtml: sayIfCardsHtml, count: total, stripPrefix: 'sayif', ni, countText: `${curIdx}/${total}`, label: '🔀 SayIf'})}
      ${renderCardStrip({cardsHtml: choiceCardsHtml, count: choices.length, stripPrefix: 'choice', ni, countText: `${choices.length}`, label: '🎯 Choice'})}
      <div class="row">
        <div class="f"><label>Identifier</label>
          <input value="${isDefault ? '默认 (default)' : esc(key)}" disabled style="opacity:.6" title="Map key，不可修改">
        </div>
        <div class="f"><label>Say ID</label><input data-b="${sp}.sayId" value="${esc(sayIf.sayId || '')}"></div>
        ${!isDefault ? `<div class="f"><label>Priority</label><input type="number" data-b="${sp}.priority" value="${sayIf.priority ?? 0}" min="0"></div>` : ''}
      </div>
      <div class="row">
        <div class="f"><label>Sound</label><input data-b="${sp}.soundEvent" value="${esc(sayIf.soundEvent || '')}" placeholder="minecraft:entity.villager.yes"></div>
      </div>
      ${renderTextSpec(`${sp}.text`, sayIf.text || {}, isDefault ? '默认文本（始终显示）' : '条件匹配时显示的文本')}
      ${isDefault ? `
      <div style="margin-top:8px">
        <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
        <div class="tiny" style="color:rgba(34,197,94,.7)">🟢 始终匹配 — 需要条件变体？回到 SayIf 区点击「＋ 添加」</div>
      </div>` : `
      <div style="margin-top:8px">
        <div class="small" style="margin-bottom:4px"><b>Conditions</b></div>
        ${(sayIf.conditions || []).map((cond, ci) =>
            renderConditionTree(`${sp}.cond.${ci}`, cond, registry, true)
        ).join('')}
        <div class="actions"><button data-cond-append="${sp}" data-cond-skip-rerender="true">＋ 添加条件</button></div>
      </div>`}
      ${!isDefault ? `
      <div class="actions" style="margin-top:12px">
        <button data-ddcondtext="${ni}:${key}">删除此 SayIf</button>
      </div>` : ''}
    </div>
  `;
}