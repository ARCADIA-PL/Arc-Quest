export function ensureValidSelection(state) {
    const sel = state.ui.sel || {t: 'quest'};
    const phaseCount = state.q.phases?.length || 0;
    if (sel.t === 'phase' || sel.t === 'obj') {
        if (!phaseCount) {
            state.ui.sel = {t: 'quest'};
            return;
        }
        if (!Number.isInteger(sel.pi) || sel.pi < 0 || sel.pi >= phaseCount) {
            state.ui.sel = {t: 'phase', pi: Math.max(0, Math.min(phaseCount - 1, Number(sel.pi) || 0))};
            return;
        }
    }
    if (sel.t === 'obj') {
        const objectives = state.q.phases[sel.pi]?.objectives || [];
        if (!objectives.length) {
            state.ui.sel = {t: 'phase', pi: sel.pi};
            return;
        }
        if (!Number.isInteger(sel.oi) || sel.oi < 0 || sel.oi >= objectives.length) {
            state.ui.sel = {t: 'phase', pi: sel.pi};
        }
    }
}

export function navigateToPath(state, rerender, path) {
    if (!path) return;
    if (path === 'quest' || path === 'visual' || path === 'rewards' || path === 'raw') {
        state.ui.sel = {t: path};
        return rerender();
    }
    if (path.startsWith('phase:')) {
        state.ui.sel = {t: 'phase', pi: Number(path.split(':')[1])};
        return rerender();
    }
    if (path.startsWith('objective:')) {
        const [, pi, oi] = path.split(':');
        state.ui.sel = {t: 'obj', pi: Number(pi), oi: Number(oi)};
        return rerender();
    }
}
