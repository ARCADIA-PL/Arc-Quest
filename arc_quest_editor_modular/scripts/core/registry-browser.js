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

function buildNamespaceGroup(nsName, entries, expanded) {
    const total = entries.length;

    const rowsHtml = expanded
        ? entries.map(e => `
            <div class="reg-entry" data-reg-id="${e.id}" tabindex="0">
                <span class="reg-entry-label">${e.label || e.id}</span>
                <span class="reg-entry-ns">${ns(e.id)}</span>
            </div>
        `).join('')
        : `<div class="reg-placeholder">${total} 个条目（点击展开）</div>`;

    return `
        <div class="reg-ns-group">
            <div class="reg-ns-header" data-reg-ns="${nsName}">
                <span class="reg-ns-arrow">${expanded ? '▾' : '▸'}</span>
                <span class="reg-ns-name">${nsName}</span>
                <span class="reg-ns-count">${total}</span>
            </div>
            <div class="reg-ns-body" style="display:${expanded ? 'block' : 'none'}">
                ${rowsHtml}
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

function groupByNamespace(entries) {
    const map = new Map();
    for (const e of entries) {
        const n = ns(e.id);
        if (!map.has(n)) map.set(n, []);
        map.get(n).push(e);
    }
    for (const [, v] of map) {
        v.sort((a, b) => (a.label || a.id).localeCompare(b.label || b.id));
    }
    return new Map(
        [...map.entries()].sort((a, b) => {
            if (a[0] === 'minecraft') return -1;
            if (b[0] === 'minecraft') return 1;
            return b[1].length - a[1].length || a[0].localeCompare(b[0]);
        })
    );
}

export function renderRegBrowser({ registryType, filter, mode, expandedGroups }) {
    const q = (filter || '').trim();
    let namespaces, total;
    let groups;

    if (q) {
        const results = RegistryClient.search(registryType, q);
        namespaces = groupByNamespace(results);
        total = results.length;
        groups = new Set([...namespaces.keys()]);
    } else {
        namespaces = RegistryClient.listByNamespace(registryType);
        total = RegistryClient.count(registryType);
        groups = expandedGroups || new Set();
    }

    const typeTabs = buildRegTypeTabs(registryType);
    const searchBar = buildSearchBar(registryType, filter || '');

    let groupsHtml = '';
    for (const [nsName, entries] of namespaces) {
        groupsHtml += buildNamespaceGroup(nsName, entries, groups.has(nsName));
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

export function bindRegBrowserEvents(rootEl, { onSelect, onTypeChange, onSearch, onToggleNamespace }, opts) {
    if (!rootEl) return;

    const signal = opts?.signal;

    rootEl.addEventListener('click', e => {
        const typeBtn = e.target.closest('[data-reg-type]');
        if (typeBtn && onTypeChange) {
            onTypeChange(typeBtn.dataset.regType);
            return;
        }

        const nsHeader = e.target.closest('[data-reg-ns]');
        if (nsHeader && onToggleNamespace) {
            onToggleNamespace(nsHeader.dataset.regNs);
            return;
        }

        const entry = e.target.closest('[data-reg-id]');
        if (entry && onSelect) {
            onSelect(entry.dataset.regId);
        }
    }, signal ? { signal } : undefined);

    rootEl.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            const entry = e.target.closest('[data-reg-id]');
            if (entry && onSelect) {
                onSelect(entry.dataset.regId);
            }
        }
    }, signal ? { signal } : undefined);

    rootEl.addEventListener('input', e => {
        if (e.target.dataset.regSearch !== undefined && onSearch) {
            onSearch(e.target.value);
        }
    }, signal ? { signal } : undefined);
}
