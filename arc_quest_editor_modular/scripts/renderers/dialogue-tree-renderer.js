import {esc} from '../core/utils.js';

export function renderDialogueTree(state, target) {
    const dialogue = state.dialogue.q;
    const nodes = dialogue.nodes || [];
    const sel = state.dialogue.ui.sel;
    const startNodeId = dialogue.startNodeId;
    const sayIfFold = state.dialogue.ui.sayIfFold;
    const choiceFold = state.dialogue.ui.choiceFold;

    const icConfig = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`;
    const icFile = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`;
    const icBubble = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>`;
    const icBranch = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="6" y1="3" x2="6" y2="21"/><polyline points="18 8 12 14 6 14"/></svg>`;

    const treeItem = (data, label, active, icon = '', extra = '') =>
        `<div class="tree ${active ? 'on' : ''}" ${data}>${icon}<span style="flex:1;margin-left:8px">${label}</span>${extra}</div>`;

    let h = `<div class="sec">`;

    h += treeItem('data-t="config"', '⚙ 对话配置', sel.t === 'config', icConfig);
    h += `<h3 style="margin-top:16px">📋 节点列表</h3>`;

    nodes.forEach((node, ni) => {
        const isStart = node.nodeId === startNodeId;
        const star = isStart ? ' ★' : '';
        const sayIfCount = Object.keys(node.conditionalTexts || {}).length;
        const choiceCount = (node.choices || []).length;
        const selThis = sel.t !== 'config' && sel.ni === ni;

        h += treeItem(
            `data-t="node" data-ni="${ni}"`,
            `${esc(node.nodeId || `#${ni + 1}`)}${star}`,
            selThis && sel.t === 'node',
            icFile,
            `<span class="chip" style="font-size:9px">S:${sayIfCount} C:${choiceCount}</span>`
        );

        if (selThis) {
            h += `<div class="sub" style="margin-left:16px;border:none;padding:0 0 4px 0">`;

            h += treeItem(
                `data-t="say" data-ni="${ni}"`,
                '📝 默认文本',
                sel.t === 'say',
                icBubble
            );

            if (sayIfCount > 0) {
                h += treeItem(
                    `data-t="toggle-sayIf" data-ni="${ni}"`,
                    `🔀 条件文本 (${sayIfCount}) ${sayIfFold ? '▸' : '▾'}`,
                    false,
                    icBranch
                );
                if (!sayIfFold) {
                    for (const [key] of Object.entries(node.conditionalTexts || {})) {
                        h += treeItem(
                            `data-t="sayIf" data-ni="${ni}" data-key="${key}"`,
                            esc(key),
                            sel.t === 'sayIf' && sel.key === key,
                            icBubble
                        );
                    }
                }
            } else {
                h += `<div class="tree" style="opacity:.4">🔀 条件文本 (0)</div>`;
            }

            if (choiceCount > 0) {
                h += treeItem(
                    `data-t="toggle-choice" data-ni="${ni}"`,
                    `🎯 选项 (${choiceCount}) ${choiceFold ? '▸' : '▾'}`,
                    false,
                    icBranch
                );
                if (!choiceFold) {
                    node.choices.forEach((ch, ci) => {
                        h += treeItem(
                            `data-t="choice" data-ni="${ni}" data-ci="${ci}"`,
                            `▶ ${esc(ch.choiceId || `#${ci + 1}`)}`,
                            sel.t === 'choice' && sel.ci === ci,
                            icBranch
                        );
                    });
                }
            } else {
                h += `<div class="tree" style="opacity:.4">🎯 选项 (0)</div>`;
            }

            h += `</div>`;
        }
    });

    h += `<div class="actions" style="margin-top:8px"><button id="addDialogueNodeBtn">＋ 添加节点</button></div>`;
    h += `</div>`;

    target.innerHTML = h;
}
