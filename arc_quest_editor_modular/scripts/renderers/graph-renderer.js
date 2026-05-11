import {esc} from '../core/utils.js';

function buildGraph(phases) {
    const map = new Map();
    const edges = [];
    phases.forEach((p, i) => map.set(p.id, {
        id: p.id,
        index: i,
        p,
        inDegree: 0,
        children: [],
        depth: 0,
        x: 0,
        y: 0,
        width: 240,
        height: 80 + Math.min((p.objectives?.length || 0) * 24, 96)
    }));
    phases.forEach(p => {
        const s = map.get(p.id);
        if (!s) return;
        const add = (tid, type) => {
            if (!map.has(tid)) return;
            const t = map.get(tid);
            t.inDegree++;
            s.children.push(t);
            edges.push({source: s, target: t, type});
        };
        if (p.mode === 'parallel') (p.parallelPhaseIds || []).forEach(id => add(id, 'parallel'));
        else if (p.mode === 'choice') (p.choicePhaseIds || []).forEach(id => add(id, 'choice'));
        else (p.transitions || []).forEach(t => add(t.targetPhaseId, 'normal'));
    });
    let roots = [...map.values()].filter(n => n.inDegree === 0);
    if (!roots.length && map.size) roots = [[...map.values()][0]];
    const seen = new Set();
    const dfs = (n, d) => {
        if (seen.has(n.id) && n.depth >= d) return;
        n.depth = Math.max(n.depth, d);
        seen.add(n.id);
        n.children.forEach(c => dfs(c, n.depth + 1));
    };
    roots.forEach(r => dfs(r, 0));
    return {nodes: [...map.values()], edges};
}

function applyLayout(nodes, orientation) {
    const layers = [];
    nodes.forEach(n => ((layers[n.depth] ||= []).push(n)));
    layers.forEach((layer, depth) => {
        if (!layer) return;
        if (orientation === 'vertical') {
            const total = layer.reduce((s, n) => s + n.width + 20, 0) - 20;
            let x = -total / 2;
            layer.forEach(n => {
                n.x = x;
                n.y = depth * 180;
                x += n.width + 20;
            });
        } else {
            const total = layer.reduce((s, n) => s + n.height + 20, 0) - 20;
            let y = -total / 2;
            layer.forEach(n => {
                n.x = depth * 320;
                n.y = y;
                y += n.height + 20;
            });
        }
    });
}

function edgeSvg(e, orientation) {
    let stroke = 'rgba(255,255,255,0.15)';
    let dash = '';
    if (e.type === 'parallel') {
        stroke = 'var(--parallel)';
        dash = 'stroke-dasharray="6 4"';
    }
    if (e.type === 'choice') {
        stroke = 'var(--choice)';
        dash = 'stroke-dasharray="3 4"';
    }

    if (orientation === 'vertical') {
        const sx = e.source.x + e.source.width / 2;
        const sy = e.source.y + e.source.height;
        const ex = e.target.x + e.target.width / 2;
        const ey = e.target.y;
        const mid = Math.max(60, (ey - sy) / 2);
        return `<path class="edge-path" data-source="${e.source.id}" data-target="${e.target.id}" d="M ${sx} ${sy} C ${sx} ${sy + mid}, ${ex} ${ey - mid}, ${ex} ${ey}" fill="none" stroke="${stroke}" stroke-width="2.5" ${dash} marker-end="url(#arrow-head)" style="transition:all .3s" />`;
    }

    const back = e.target.x <= e.source.x;
    const sx = e.source.x + e.source.width;
    const sy = e.source.y + 40;
    const ex = e.target.x;
    const ey = e.target.y + 40;
    const d = back
        ? `M ${sx} ${sy} C ${sx + 60} ${sy}, ${sx + 60} ${sy + 80}, ${(sx + ex) / 2} ${sy + 80} S ${ex - 60} ${ey}, ${ex} ${ey}`
        : `M ${sx} ${sy} C ${sx + Math.max(80, (ex - sx) / 2)} ${sy}, ${ex - Math.max(80, (ex - sx) / 2)} ${ey}, ${ex} ${ey}`;
    return `<path class="edge-path" data-source="${e.source.id}" data-target="${e.target.id}" d="${d}" fill="none" stroke="${stroke}" stroke-width="2.5" ${dash} marker-end="url(#arrow-head)" style="transition:all .3s" />`;
}

function nodeHtml(n, sel, err) {
    let c = 'var(--accent)';
    if (n.p.mode === 'parallel') c = 'var(--parallel)';
    if (n.p.mode === 'choice') c = 'var(--choice)';
    const border = err ? 'border-color:var(--danger)' : (sel ? `border-color:${c};box-shadow:0 0 0 1px ${c} inset,0 8px 30px rgba(0,0,0,.4);` : '');
    let objs = '';
    (n.p.objectives || []).slice(0, 3).forEach(o => {
        objs += `<div class="node-obj"><span></span> ${esc(o.type)}</div>`;
    });
    if ((n.p.objectives || []).length > 3) objs += `<div class="node-obj" style="opacity:.5;justify-content:center">... +${n.p.objectives.length - 3} more</div>`;
    return `<foreignObject x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" class="node-fo" data-id="${n.p.id}" data-index="${n.index}"><div class="q-node" style="${border}"><div class="q-node-bar" style="background:${c}"></div><div class="q-node-header"><div class="q-node-title">${esc(n.p.id)}</div><div class="q-node-mode">${(n.p.mode || 'normal').toUpperCase()}</div></div><div class="q-node-body">${objs}</div></div></foreignObject>`;
}

function controlsHtml() {
    return `<div class="graph-controls graph-controls-top-right"><button class="g-scale-100" title="100%">100%</button><button class="g-fit" title="Fit">Fit</button><button class="g-reset" title="Reset">Reset</button></div>`;
}

function viewportHtml(state, cls = '') {
    const orientation = cls.includes('compact') ? 'vertical' : 'horizontal';
    const {nodes, edges} = buildGraph(state.q.phases || []);
    applyLayout(nodes, orientation);
    const ns = nodes.map(n => nodeHtml(n, state.ui.sel.t === 'phase' && state.ui.sel.pi === n.index, state.diag.some(d => d.path.startsWith(`phase:${n.index}`)))).join('');
    return `<div class="graph-viewport ${cls}" data-orientation="${orientation}"><svg width="100%" height="100%" class="graph-svg"><defs><marker id="arrow-head" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 1 L 10 5 L 0 9 z" fill="rgba(255,255,255,0.4)" /></marker></defs><g class="graph-canvas" style="transform-origin:0 0">${edges.map(e => edgeSvg(e, orientation)).join('')}${ns}</g></svg>${controlsHtml()}</div>`;
}

function ensureOverlay() {
    let el = document.querySelector('#graphGlobalOverlay');
    if (el) return el;
    el = document.createElement('div');
    el.id = 'graphGlobalOverlay';
    el.className = 'graph-global-overlay';
    el.innerHTML = '<div class="graph-global-backdrop" data-graph-backdrop></div><div class="graph-global-panel" data-graph-panel><button class="graph-side-toggle graph-side-toggle-expanded" data-graph-toggle="collapse" title="收起拓扑图"><span class="graph-side-toggle-icon">⤡</span><span class="graph-side-toggle-text">收起</span></button><div class="graph-global-body" data-graph-body></div></div>';
    document.body.appendChild(el);
    return el;
}

function placeOverlay(shell, overlay) {
    const compact = shell.querySelector('.graph-viewport-compact');
    const panel = overlay.querySelector('[data-graph-panel]');
    if (!compact || !panel) return;
    const r = compact.getBoundingClientRect();
    const width = Math.min(window.innerWidth - 24, Math.max(920, Math.round(window.innerWidth * 0.72)));
    const height = Math.min(window.innerHeight - 32, Math.max(520, Math.round(r.height)));
    panel.style.width = `${width}px`;
    panel.style.height = `${height}px`;
    panel.style.top = `${Math.max(16, Math.round(r.top - (height - r.height) / 2))}px`;
    panel.style.right = '12px';
}

export function renderGraph(state) {
    return `<div class="graph-shell"><div class="graph-compact-row"><button class="graph-side-toggle" data-graph-toggle="expand" title="展开拓扑图"><span class="graph-side-toggle-icon">⤢</span><span class="graph-side-toggle-text">展开</span></button><div class="graph-wrapper fade-in">${viewportHtml(state, 'graph-viewport-compact')}</div></div></div>`;
}

export function bindGraphEvents(viewport, state, onClick) {
    const canvas = viewport.querySelector('.graph-canvas');
    const view = state.ui.graphView;
    let dragging = false;
    let startX = 0;
    let startY = 0;
    const update = () => {
        canvas.style.transform = `translate(${view.x}px, ${view.y}px) scale(${view.k})`;
    };
    const fit = () => {
        const nodes = canvas.querySelectorAll('.node-fo');
        if (!nodes.length) return;
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        nodes.forEach(n => {
            const x = parseFloat(n.getAttribute('x')), y = parseFloat(n.getAttribute('y')),
                w = parseFloat(n.getAttribute('width')), h = parseFloat(n.getAttribute('height'));
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x + w > maxX) maxX = x + w;
            if (y + h > maxY) maxY = y + h;
        });
        minX -= 40;
        minY -= 40;
        maxX += 40;
        maxY += 40;
        const cw = maxX - minX, ch = maxY - minY, vw = viewport.clientWidth, vh = viewport.clientHeight;
        const maxScale = viewport.classList.contains('graph-viewport-compact') ? 1.2 : 1.5;
        view.k = Math.max(.18, Math.min(maxScale, Math.min(vw / cw, vh / ch)));
        view.x = (vw - cw * view.k) / 2 - minX * view.k;
        view.y = (vh - ch * view.k) / 2 - minY * view.k;
        update();
    };
    const reset = () => {
        view.x = 0;
        view.y = 0;
        view.k = 1;
        fit();
    };
    const setHundred = () => {
        view.k = 1;
        update();
    };
    if (view.x === 0 && view.y === 0 && view.k === 1) fit(); else update();
    viewport.__graphApi = {fitToView: fit, updateTransform: update, resetView: reset};
    viewport.addEventListener('mousedown', e => {
        if (e.target.closest('.graph-controls') || e.target.closest('.node-fo')) return;
        dragging = true;
        startX = e.clientX - view.x;
        startY = e.clientY - view.y;
        viewport.style.cursor = 'grabbing';
    });
    window.addEventListener('mousemove', e => {
        if (!dragging) return;
        view.x = e.clientX - startX;
        view.y = e.clientY - startY;
        requestAnimationFrame(update);
    });
    window.addEventListener('mouseup', () => {
        dragging = false;
        viewport.style.cursor = 'grab';
    });
    viewport.addEventListener('wheel', e => {
        e.preventDefault();
        const r = viewport.getBoundingClientRect(), px = e.clientX - r.left, py = e.clientY - r.top;
        const tx = (px - view.x) / view.k, ty = (py - view.y) / view.k;
        view.k = Math.max(.1, Math.min(3, view.k * (e.deltaY > 0 ? .85 : 1.15)));
        view.x = px - tx * view.k;
        view.y = py - ty * view.k;
        update();
    }, {passive: false});
    viewport.querySelector('.g-scale-100').onclick = e => {
        e.stopPropagation();
        setHundred();
    };
    viewport.querySelector('.g-fit').onclick = e => {
        e.stopPropagation();
        fit();
    };
    viewport.querySelector('.g-reset').onclick = e => {
        e.stopPropagation();
        reset();
    };
    const edges = canvas.querySelectorAll('.edge-path'), nodes = canvas.querySelectorAll('.node-fo');
    nodes.forEach(node => {
        node.onclick = e => {
            e.stopPropagation();
            onClick(Number(node.dataset.index));
        };
        node.onmouseenter = () => {
            const id = node.dataset.id;
            nodes.forEach(n => {
                if (n !== node) n.style.opacity = '.3';
            });
            edges.forEach(edge => {
                if (edge.dataset.source === id || edge.dataset.target === id) {
                    edge.style.opacity = '1';
                    edge.style.strokeWidth = '4';
                    const other = canvas.querySelector(`.node-fo[data-id="${edge.dataset.source === id ? edge.dataset.target : edge.dataset.source}"]`);
                    if (other) other.style.opacity = '1';
                } else edge.style.opacity = '.1';
            });
        };
        node.onmouseleave = () => {
            nodes.forEach(n => n.style.opacity = '1');
            edges.forEach(edge => {
                edge.style.opacity = '1';
                edge.style.strokeWidth = '2.5';
            });
        };
    });
}

export function bindGraphShellEvents(shell, state, onClick) {
    const overlay = ensureOverlay();
    const body = overlay.querySelector('[data-graph-body]');
    const close = () => {
        state.ui.graphExpanded = false;
        overlay.classList.remove('open');
        document.body.classList.remove('graph-expanded-open');
    };
    const open = () => {
        state.ui.graphExpanded = true;
        body.innerHTML = viewportHtml(state, 'graph-viewport-expanded');
        placeOverlay(shell, overlay);
        overlay.classList.add('open');
        document.body.classList.add('graph-expanded-open');
        const vp = body.querySelector('.graph-viewport-expanded');
        if (vp) {
            bindGraphEvents(vp, state, onClick);
            requestAnimationFrame(() => vp.__graphApi?.fitToView());
        }
    };
    shell.querySelector('[data-graph-toggle="expand"]').onclick = e => {
        e.stopPropagation();
        open();
    };
    overlay.querySelector('[data-graph-backdrop]').onclick = close;
    overlay.querySelector('[data-graph-toggle="collapse"]').onclick = e => {
        e.stopPropagation();
        close();
    };
    if (!overlay.__resizeBound) {
        overlay.__resizeBound = true;
        window.addEventListener('resize', () => {
            if (!overlay.classList.contains('open')) return;
            const activeShell = document.querySelector('.graph-shell');
            if (!activeShell) return;
            placeOverlay(activeShell, overlay);
            overlay.querySelector('.graph-viewport-expanded')?.__graphApi?.fitToView();
        });
    }
}
