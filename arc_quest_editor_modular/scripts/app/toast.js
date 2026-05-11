export function showToast(dom, title, message, kind = 'info', duration = 2400) {
    if (!dom.toastStack) return;
    const toast = document.createElement('div');
    toast.className = `toast ${kind}`;
    toast.innerHTML = `<div class="toast-title">${title}</div><div class="toast-msg">${message}</div>`;
    dom.toastStack.appendChild(toast);
    window.setTimeout(() => {
        toast.remove();
    }, duration);
}

export function setDropOverlayVisible(dom, visible, message = '松开以导入 Arc Quest 任务文件') {
    if (dom.dropOverlay) {
        dom.dropOverlay.classList.toggle('visible', visible);
        dom.dropOverlay.setAttribute('aria-hidden', visible ? 'false' : 'true');
    }
    if (dom.mainLayout) dom.mainLayout.classList.toggle('drag-target', visible);
    if (dom.dropSubtitle) dom.dropSubtitle.textContent = message;
}
