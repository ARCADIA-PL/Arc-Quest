import {renderRegBrowser, bindRegBrowserEvents, REG_BROWSER_CLASS} from './registry-browser.js';

const ATTR = 'data-ac-registry';
const DROPDOWN_ID = 'arc-ac-popup';

let activeInput = null;
let activeRegistry = null;
let popupDiv = null;
let popupType = 'items';
let popupFilter = '';

function ns(id) {
    const colon = (id || '').indexOf(':');
    return colon >= 0 ? id.substring(0, colon) : '?';
}

function ensurePopup() {
    if (popupDiv) return popupDiv;
    popupDiv = document.createElement('div');
    popupDiv.id = DROPDOWN_ID;
    popupDiv.className = REG_BROWSER_CLASS + ' arc-ac-popup';
    popupDiv.style.display = 'none';
    document.body.appendChild(popupDiv);
    bindRegBrowserEvents(popupDiv, {
        onSelect: onPopupSelect,
        onTypeChange: onPopupTypeChange,
        onSearch: onPopupSearch
    });
    return popupDiv;
}

function positionPopup(input) {
    const rect = input.getBoundingClientRect();
    const dd = ensurePopup();
    dd.style.left = rect.left + 'px';
    dd.style.top = (rect.bottom + 4) + 'px';
    dd.style.minWidth = Math.max(rect.width, 320) + 'px';
}

function showPopup(input, registryName) {
    activeInput = input;
    activeRegistry = registryName;
    popupType = registryName;
    popupFilter = input.value || '';

    const dd = ensurePopup();
    dd.innerHTML = renderRegBrowser({
        registryType: popupType,
        filter: popupFilter,
        mode: 'popup',
        expandedGroups: new Set(['minecraft'])
    });
    positionPopup(input);
    dd.style.display = 'block';

    requestAnimationFrame(() => {
        const searchInput = dd.querySelector('[data-reg-search]');
        if (searchInput) searchInput.focus();
    });
}

function hidePopup() {
    if (popupDiv) {
        popupDiv.style.display = 'none';
    }
    activeInput = null;
}

function onPopupSelect(id) {
    if (activeInput) {
        const setter = Object.getOwnPropertyDescriptor(
            window.HTMLInputElement.prototype, 'value'
        ).set;
        setter.call(activeInput, id);
        activeInput.dispatchEvent(new Event('input', { bubbles: true }));
        activeInput.dispatchEvent(new Event('change', { bubbles: true }));
    }
    hidePopup();
    if (activeInput) activeInput.focus();
}

function onPopupTypeChange(type) {
    popupType = type;
    const dd = ensurePopup();
    dd.innerHTML = renderRegBrowser({
        registryType: popupType,
        filter: popupFilter,
        mode: 'popup',
        expandedGroups: new Set(['minecraft'])
    });
    bindRegBrowserEvents(dd, {
        onSelect: onPopupSelect,
        onTypeChange: onPopupTypeChange,
        onSearch: onPopupSearch
    });
}

function onPopupSearch(filter) {
    popupFilter = filter;
    const dd = ensurePopup();
    dd.innerHTML = renderRegBrowser({
        registryType: popupType,
        filter: popupFilter,
        mode: 'popup',
        expandedGroups: new Set()
    });
    bindRegBrowserEvents(dd, {
        onSelect: onPopupSelect,
        onTypeChange: onPopupTypeChange,
        onSearch: onPopupSearch
    });
}

function handleFocus(e) {
    const registryName = e.target.getAttribute(ATTR);
    if (!registryName) return;
    showPopup(e.target, registryName);
}

function handleClick(e) {
    if (popupDiv && popupDiv.style.display !== 'none') {
        if (popupDiv.contains(e.target)) return;
        if (e.target === activeInput) return;
    }

    const registryName = e.target.getAttribute(ATTR);
    if (registryName && e.target !== activeInput) {
        showPopup(e.target, registryName);
        return;
    }

    if (activeInput && e.target !== activeInput && popupDiv && !popupDiv.contains(e.target)) {
        hidePopup();
    }
}

document.addEventListener('click', handleClick, true);

document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && popupDiv && popupDiv.style.display !== 'none') {
        hidePopup();
        if (activeInput) activeInput.focus();
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
    hidePopup();
}
