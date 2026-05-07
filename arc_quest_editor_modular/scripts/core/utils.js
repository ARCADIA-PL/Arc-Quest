export const qs = s => document.querySelector(s);
export const esc = s => String(s ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
export const num = v => v === '' ? 0 : Number(v);
export const bool = v => v === true || v === 'true';
export const clr = v => /^#?[\da-fA-F]{6}$/.test(v || '') ? (v[0] === '#' ? v : '#' + v) : '#63c7ff';
export const splitList = v => String(v || '').split(',').map(x => x.trim()).filter(Boolean);
export const phaseModeColor = mode => mode === 'parallel' ? 'var(--parallel)' : mode === 'choice' ? 'var(--choice)' : 'var(--a)';
export const phaseModeBadge = mode => mode === 'parallel' ? '<span class="phase-parallel">parallel</span>' : mode === 'choice' ? '<span class="phase-choice">choice</span>' : '<span class="phase-normal">normal</span>';
