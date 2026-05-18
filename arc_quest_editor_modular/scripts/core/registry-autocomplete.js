import {renderRegBrowser, bindRegBrowserEvents} from './registry-browser.js';

const ATTR = 'data-ac-registry';
const BACKDROP_ID = 'arc-reg-backdrop';
const MODAL_ID = 'arc-reg-modal';

let activeInput = null;
let modalType = 'items';
let modalFilter = '';
let dragState = null;
let modalAc = null;
let modalExpanded = new Set();
let searchTimer = null;

function ensureBackdrop() {
    let bd = document.getElementById(BACKDROP_ID);
    if (!bd) {
        bd = document.createElement('div');
        bd.id = BACKDROP_ID;
        bd.className = 'reg-modal-backdrop';
        bd.addEventListener('click', () => hideModal());
        document.body.appendChild(bd);
    }
    return bd;
}

function ensureModal() {
    let md = document.getElementById(MODAL_ID);
    if (!md) {
        md = document.createElement('div');
        md.id = MODAL_ID;
        md.className = 'reg-modal';
        md.innerHTML = `
            <div class="reg-modal-header">
                <span class="reg-modal-title">📦 选择注册表条目</span>
                <button class="reg-modal-close" type="button">&times;</button>
            </div>
            <div class="reg-modal-body"></div>
        `;
        md.querySelector('.reg-modal-close').addEventListener('click', () => hideModal());
        bindDrag(md);
        document.body.appendChild(md);
    }
    return md;
}

function bindDrag(modal) {
    const header = modal.querySelector('.reg-modal-header');
    if (!header) return;

    header.addEventListener('mousedown', e => {
        if (e.target.closest('.reg-modal-close')) return;
        e.preventDefault();
        const rect = modal.getBoundingClientRect();
        dragState = {
            startX: e.clientX,
            startY: e.clientY,
            left: rect.left,
            top: rect.top
        };
        header.style.cursor = 'grabbing';
    });
}

document.addEventListener('mousemove', e => {
    if (!dragState) return;
    const dx = e.clientX - dragState.startX;
    const dy = e.clientY - dragState.startY;
    const modal = document.getElementById(MODAL_ID);
    if (modal) {
        modal.style.left = (dragState.left + dx) + 'px';
        modal.style.top = (dragState.top + dy) + 'px';
        modal.style.transform = 'none';
    }
});

document.addEventListener('mouseup', () => {
    if (!dragState) return;
    dragState = null;
    const modal = document.getElementById(MODAL_ID);
    if (modal) {
        const header = modal.querySelector('.reg-modal-header');
        if (header) header.style.cursor = 'grab';
    }
});

function fillModal() {
    if (modalAc) modalAc.abort();
    modalAc = new AbortController();

    const md = ensureModal();
    const body = md.querySelector('.reg-modal-body');
    body.innerHTML = renderRegBrowser({
        registryType: modalType,
        filter: modalFilter,
        mode: 'panel',
        expandedGroups: modalExpanded
    });
    bindRegBrowserEvents(body, {
        onSelect: onModalSelect,
        onTypeChange: onModalTypeChange,
        onSearch: onModalSearch,
        onToggleNamespace: onModalToggleNs
    }, { signal: modalAc.signal });

    requestAnimationFrame(() => {
        const si = body.querySelector('[data-reg-search]');
        if (si && document.activeElement !== si) {
            si.focus();
            si.setSelectionRange(si.value.length, si.value.length);
        }
    });
}

function setTitle(registryName) {
    const md = ensureModal();
    const title = md.querySelector('.reg-modal-title');
    const labels = {items:'物品',entityTypes:'实体',blocks:'方块',soundEvents:'音效',mobEffects:'药水效果',biomes:'生物群系'};
    title.textContent = '📦 选择 ' + (labels[registryName] || registryName);
}

function showModal(input, registryName) {
    activeInput = input;
    modalType = registryName;
    modalFilter = input.value || '';
    modalExpanded = new Set();

    setTitle(registryName);
    fillModal();

    const backdrop = ensureBackdrop();
    backdrop.style.display = 'block';
    const md = ensureModal();
    md.style.display = 'flex';

    if (!dragState) {
        md.style.left = '50%';
        md.style.top = '50%';
        md.style.transform = 'translate(-50%,-50%)';
    }

    requestAnimationFrame(() => {
        const si = md.querySelector('[data-reg-search]');
        if (si) si.focus();
    });
}

function hideModal() {
    if (modalAc) { modalAc.abort(); modalAc = null; }
    if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; }
    const bd = document.getElementById(BACKDROP_ID);
    const md = document.getElementById(MODAL_ID);
    if (bd) bd.style.display = 'none';
    if (md) md.style.display = 'none';
    activeInput = null;
}

function onModalSelect(id) {
    if (activeInput) {
        const setter = Object.getOwnPropertyDescriptor(
            window.HTMLInputElement.prototype, 'value'
        ).set;
        setter.call(activeInput, id);
        activeInput.dispatchEvent(new Event('input', { bubbles: true }));
        activeInput.dispatchEvent(new Event('change', { bubbles: true }));
    }
    hideModal();
    if (activeInput) activeInput.focus();
}

function onModalTypeChange(type) {
    modalType = type;
    modalFilter = '';
    modalExpanded = new Set();
    setTitle(type);
    fillModal();
}

function onModalSearch(filter) {
    modalFilter = filter;
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
        modalExpanded = filter.trim() ? new Set() : modalExpanded;
        fillModal();
    }, 150);
}

function onModalToggleNs(nsName) {
    if (modalExpanded.has(nsName)) {
        modalExpanded.delete(nsName);
    } else {
        modalExpanded.add(nsName);
    }
    fillModal();
}

function handleFocus(e) {
    const registryName = e.target.getAttribute(ATTR);
    if (!registryName) return;
    showModal(e.target, registryName);
}

document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
        const md = document.getElementById(MODAL_ID);
        if (md && md.style.display !== 'none') {
            hideModal();
            if (activeInput) activeInput.focus();
        }
    }
});

export function bindAll(rootEl) {
    const inputs = (rootEl || document).querySelectorAll('[' + ATTR + ']');
    inputs.forEach(input => {
        input.removeEventListener('focus', handleFocus);
        input.addEventListener('focus', handleFocus);
    });
}

export function unbindAll(rootEl) {
    const inputs = (rootEl || document).querySelectorAll('[' + ATTR + ']');
    inputs.forEach(input => {
        input.removeEventListener('focus', handleFocus);
    });
    hideModal();
}
