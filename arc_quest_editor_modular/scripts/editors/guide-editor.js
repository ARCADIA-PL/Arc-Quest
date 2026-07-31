const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

export function renderGuideEditor(state, dom) {
    const guide = state.guide.q;
    const category = state.guide.kind === 'guideCategory';
    dom.left.innerHTML = `<div class="tree active"><b>${category ? 'Guide Category' : 'Guide'}</b><div class="tiny">${esc(guide.id || 'unnamed')}</div></div>`;
    dom.mid.innerHTML = `<div class="center-content"><div class="sec">
      <div class="row"><div class="f"><label>Document Type</label><select data-guide-kind><option value="guide" ${!category?'selected':''}>Guide</option><option value="guideCategory" ${category?'selected':''}>Guide Category</option></select></div>
      <div class="f"><label>ID</label><input data-guide-field="id" value="${esc(guide.id)}"></div></div>
      <div class="f"><label>Datapack JSON</label><textarea data-guide-json rows="28">${esc(JSON.stringify(guide, null, 2))}</textarea></div>
      <div class="small">Raw JSON editor preserves every Guide condition, page and media field while using the same Java datapack schema.</div>
    </div></div>`;
    dom.right.innerHTML = `<div class="sec"><h3>Validation</h3>${state.guide.diag.length ? state.guide.diag.map(issue => `<div class="diag ${issue.lvl}"><b>${esc(issue.path)}</b><div>${esc(issue.msg)}</div></div>`).join('') : '<div class="diag info">No schema errors</div>'}</div>`;
    dom.tabs.innerHTML = '';
    dom.status.innerHTML = `<span>${esc(state.guide.meta.file)}</span><span style="margin-left:auto">${category ? 'Guide Category' : 'Guide'} ? ${state.guide.diag.length} issues</span>`;
}

export function bindGuideEditor(state, rerender, mid) {
    mid.querySelector('[data-guide-kind]')?.addEventListener('change', event => {
        state.guide.kind = event.target.value;
        state.guide.q = event.target.value === 'guideCategory'
            ? {id:'',displayName:{mode:'literal',value:'',args:[]},themeColor:0x4FC3F7,sortOrder:0,iconTexture:''}
            : {id:'',category:'arc_quest:basics',title:{mode:'literal',value:'',args:[]},summary:{mode:'literal',value:'',args:[]},sortOrder:0,hidden:false,repeatablePopup:false,icon:'',renderLargeIconOnIntro:false,showUnlockPopup:false,renderPopupBackground:false,popupBackground:'',unlockConditions:[],pages:[]};
        rerender();
    });
    mid.querySelector('[data-guide-json]')?.addEventListener('change', event => {
        try {
            state.guide.q = JSON.parse(event.target.value);
            state.guide.meta.dirty = true;
            rerender();
        } catch (error) {
            state.guide.diag = [{lvl:'err',path:'json',msg:error.message}];
            rerender();
        }
    });
    mid.querySelector('[data-guide-field="id"]')?.addEventListener('change', event => {
        state.guide.q.id = event.target.value;
        state.guide.meta.dirty = true;
        rerender();
    });
}
