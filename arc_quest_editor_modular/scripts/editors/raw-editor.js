export function renderRawEditor(state, esc) {
    return `<div class="sec"><h3>Raw JSON</h3><div class="json">${esc(JSON.stringify(state.q, null, 2))}</div></div>`;
}
