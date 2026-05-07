export function renderStatus(state, statusEl) {
  const errs = state.diag.filter(x => x.lvl === 'err').length;
  const warns = state.diag.filter(x => x.lvl === 'warn').length;
  statusEl.textContent = `文件: ${state.meta.file} | 状态: ${state.meta.dirty ? '未导出' : '已同步'} | Error: ${errs} | Warning: ${warns} | 当前: ${state.ui.sel.t}`;
}
