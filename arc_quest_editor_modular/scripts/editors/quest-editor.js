import {renderQuestInfoSection} from './quest/quest-basic-section.js';
import {renderQuestTopLevelSection} from './quest/quest-top-level-section.js';
import {renderQuestCollectionWorkspace} from './quest/quest-collection-section.js';

export function renderQuestEditor(state, field, area) {
    const q = state.quest.q;
    const phaseIds = (q.phases || []).map(p => p.id).filter(Boolean);

    return `
    <div class="sec">
      <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin-bottom: 20px;">架构参数设定 (Quest Info)</h3>
      ${renderQuestInfoSection(q, field, area)}
      ${renderQuestTopLevelSection(q, phaseIds, field, area)}
      ${renderQuestCollectionWorkspace(state, q, field, phaseIds)}
      <div class="actions" style="margin-top: 24px;">
        <button id="addPhaseBtn" class="primary">
          <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M12 5v14M5 12h14"/></svg> 新增执行阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}
