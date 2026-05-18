import RegistryClient from './registry-client.js';

const REG_TYPES = [
    { key: 'items', label: '物品' },
    { key: 'entityTypes', label: '实体' },
    { key: 'blocks', label: '方块' },
    { key: 'soundEvents', label: '音效' },
    { key: 'mobEffects', label: '药水效果' },
    { key: 'biomes', label: '生物群系' }
];

const PANEL_CLASS = 'reg-browser';
const POPUP_CLASS = 'reg-browser--popup';
const STATE_CLASS = 'reg-browser--expanded';

export const REG_BROWSER_CLASS = PANEL_CLASS;

function ns(id) {
    const colon = (id || '').indexOf(':');
    return colon >= 0 ? id.substring(0, colon) : '?';
}

function buildRegTypeTabs(currentType) {
    return REG_TYPES.map(t =>
        `<button class="reg-type-tab${t.key === currentType ? ' active' : ''}" data-reg-type="${t.key}">${t.label}</button>`
    ).join('');
}

function buildNamespaceGroup(nsName, entries, filter, expandedGroups) {
    const isExpanded = expandedGroups.has(nsName);
    const count = entries.length;

    let rows;
    if (filter) {
        const q = filter.toLowerCase();
        rows = entries.filter(e =>
            e.id.toLowerCase().includes(q)
            || (e.label && e.label.toLowerCase().includes(q))
            || ns(e.id).toLowerCase().includes(q)
        );
    } else {
        rows = entries;
    }

    if (rows.length === 0) return '';

    return `
        <div class="reg-ns-group">
            <div class="reg-ns-header" data-reg-ns="${nsName}">
                <span class="reg-ns-arrow">${isExpanded ? '▾' : '▸'}</span>
                <span class="reg-ns-name">${nsName}</span>
                <span class="reg-ns-count">${count}</span>
            </div>
            <div class="reg-ns-body" style="display:${isExpanded ? 'block' : 'none'}">
                ${rows.map(e => `
                    <div class="reg-entry" data-reg-id="${e.id}" tabindex="0">
                        <span class="reg-entry-label">${e.label || e.id}</span>
                        <span class="reg-entry-ns">${ns(e.id)}</span>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
}

function buildSearchBar(registryType, filter) {
    const connected = RegistryClient.isConnected();
    return `
        <div class="reg-search-row">
            <input class="reg-search-input" type="text" placeholder="搜索 ${REG_TYPES.find(t => t.key === registryType)?.label || ''}..." value="${filter || ''}" data-reg-search>
            <span class="reg-conn-dot${connected ? ' conn-on' : ''}" title="${connected ? '已连接 MC' : '离线（缓存数据）'}"></span>
        </div>
    `;
}

export function renderRegBrowser({ registryType, filter, onSelect, mode, expandedGroups }) {
    const groups = expandedGroups || new Set(['minecraft']);
    const items = RegistryClient.listByNamespace(registryType);
    const total = RegistryClient.count(registryType);

    const typeTabs = buildRegTypeTabs(registryType);
    const searchBar = buildSearchBar(registryType, filter || '');

    let groupsHtml = '';
    for (const [nsName, entries] of items) {
        groupsHtml += buildNamespaceGroup(nsName, entries, filter || '', groups);
    }
    if (!groupsHtml) {
        groupsHtml = '<div class="reg-empty">暂无数据</div>';
    }

    return `
        <div class="${PANEL_CLASS}${mode === 'popup' ? ' ' + POPUP_CLASS : ''}">
            <div class="reg-type-tabs">${typeTabs}</div>
            ${searchBar}
            <div class="reg-groups">${groupsHtml}</div>
            <div class="reg-footer">共 ${total} 个条目</div>
        </div>
    `;
}

export function bindRegBrowserEvents(rootEl, { onSelect, onTypeChange, onSearch }) {
    if (!rootEl) return;

    rootEl.addEventListener('click', e => {
        const typeBtn = e.target.closest('[data-reg-type]');
        if (typeBtn && onTypeChange) {
            onTypeChange(typeBtn.dataset.regType);
            return;
        }

        const nsHeader = e.target.closest('[data-reg-ns]');
        if (nsHeader) {
            const nsName = nsHeader.dataset.regNs;
            const body = nsHeader.nextElementSibling;
            const arrow = nsHeader.querySelector('.reg-ns-arrow');
            if (body) {
                const isHidden = body.style.display === 'none';
                body.style.display = isHidden ? 'block' : 'none';
                if (arrow) arrow.textContent = isHidden ? '▾' : '▸';
            }
            return;
        }

        const entry = e.target.closest('[data-reg-id]');
        if (entry && onSelect) {
            onSelect(entry.dataset.regId);
        }
    });

    rootEl.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            const entry = e.target.closest('[data-reg-id]');
            if (entry && onSelect) {
                onSelect(entry.dataset.regId);
            }
        }
    });

    rootEl.addEventListener('input', e => {
        if (e.target.dataset.regSearch !== undefined && onSearch) {
            onSearch(e.target.value);
        }
    });
}
