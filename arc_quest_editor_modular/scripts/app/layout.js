export function applyPaneLayout(state, dom) {
    if (!dom.mainLayout) return;
    const left = Math.max(220, Math.min(520, Number(state.quest.ui.paneSizes?.left) || 280));
    const right = Math.max(300, Math.min(720, Number(state.quest.ui.paneSizes?.right) || 420));
    dom.mainLayout.style.gridTemplateColumns = `${left}px 8px minmax(420px, 1fr) 8px ${right}px`;
}

export function bindPaneResizers(state, dom) {
    if (!dom.mainLayout || !dom.resizerLeft || !dom.resizerRight) return;

    const minLeft = 220;
    const maxLeft = 520;
    const minRight = 280;
    const maxRight = 720;
    const minCenter = 420;

    const startDrag = side => event => {
        event.preventDefault();
        const rect = dom.mainLayout.getBoundingClientRect();
        const startX = event.clientX;
        const startLeft = Number(state.quest.ui.paneSizes.left) || 280;
        const startRight = Number(state.quest.ui.paneSizes.right) || 420;

        document.body.classList.add('is-resizing-panes');

        const onMove = moveEvent => {
            const dx = moveEvent.clientX - startX;
            if (side === 'left') {
                const nextLeft = Math.max(minLeft, Math.min(maxLeft, startLeft + dx));
                const centerWidth = rect.width - nextLeft - startRight - 16;
                if (centerWidth >= minCenter) {
                    state.quest.ui.paneSizes.left = nextLeft;
                    applyPaneLayout(state, dom);
                }
            } else {
                const nextRight = Math.max(minRight, Math.min(maxRight, startRight - dx));
                const centerWidth = rect.width - startLeft - nextRight - 16;
                if (centerWidth >= minCenter) {
                    state.quest.ui.paneSizes.right = nextRight;
                    applyPaneLayout(state, dom);
                }
            }
        };

        const onUp = () => {
            document.body.classList.remove('is-resizing-panes');
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };

        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    };

    dom.resizerLeft.onmousedown = startDrag('left');
    dom.resizerRight.onmousedown = startDrag('right');
}
