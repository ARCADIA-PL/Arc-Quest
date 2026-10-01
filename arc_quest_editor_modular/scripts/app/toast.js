export function showToast(dom, title, message, kind = 'info', duration = 2400) {
    if (!dom.toastStack) return;
    const toast = document.createElement('div');
    toast.className = `toast ${kind}`;
    const heading = document.createElement('div');
    heading.className = 'toast-title';
    heading.textContent = title;
    const content = document.createElement('div');
    content.className = 'toast-msg';
    content.textContent = message;
    toast.append(heading, content);
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
