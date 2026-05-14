import {renderRewardList} from './reward-editor.js';
import {renderPhaseModeSummary} from './shared.js';
import {
    renderPhaseHeaderSection,
    renderPhaseFlowSection,
    renderPhaseTransitionsSection,
    renderPhaseChoicesSection,
    renderPhaseCollectionSection
} from './phase-editor-sections.js';

export function renderPhaseEditor(state, field, area) {
    const s = state.quest.ui.sel;
    const p = state.quest.q.phases[s.pi];
    const phaseIds = (state.quest.q.phases || []).map(x => x.id).filter(Boolean);
    const isCollectionQuest = state.quest.q.mode === 'COLLECTION';
    const registry = state.registry;

    return `
    <div class="sec">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;">
        <h3 style="font-size:20px; font-weight:700; color:var(--text-main); margin:0;">阶段节点配置 (Phase: ${s.pi})</h3>
        ${renderPhaseModeSummary(p)}
      </div>

      ${renderPhaseHeaderSection(s, p, field, area)}
      ${renderPhaseFlowSection(s, p, phaseIds, field, area, registry)}
      ${renderPhaseTransitionsSection(s, p, phaseIds, registry)}
      ${renderPhaseChoicesSection(s, p, phaseIds, field, registry)}

      <h4>阶段目标 (Objectives)</h4>
      <div class="card" style="background: rgba(0,0,0,0.2)">
        ${(p.objectives || []).map((o, oi) => `
          <div class="card" style="margin: 8px 0; border-color: rgba(255,255,255,0.1);">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div>
                <b style="color:var(--accent); font-size:14px;">${oi + 1}. ${o.type}</b>
                <div class="small" style="margin-top:4px; font-family:monospace">${o.id || ''}</div>
              </div>
              <div class="actions" style="margin-top:0;">
                <button data-open="${oi}">编辑配置</button>
                <button data-do="${oi}" class="danger">移除</button>
              </div>
            </div>
          </div>
        `).join('')}
        <div class="actions"><button id="addObjectiveBtn" class="primary">+ 添加新目标 (Objective)</button></div>
      </div>

      ${isCollectionQuest ? renderPhaseCollectionSection(s, p, field, area) : `
        <h4>Collection Entry Config</h4>
        <div class="card"><div class="small">当前 Quest Mode 为 PROGRESSION，Collection 子配置仅在 COLLECTION 模式下显示。</div></div>
      `}

      <h4>阶段奖励 (Phase Rewards)</h4>
      <div class="card">
        ${renderRewardList(p.rewards, 'phase', s.pi, field)}
        <div class="actions"><button id="addPhaseRewardBtn">+ 添加奖励</button></div>
      </div>

      <div class="actions" style="margin-top:40px; padding-top:20px; border-top:1px dashed var(--card-border);">
        <button id="movePhaseUpBtn">↑ 上移节点</button>
        <button id="movePhaseDownBtn">↓ 下移节点</button>
        <button id="deletePhaseBtn" class="danger" style="margin-left:auto;">
          <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg> 删除此阶段 (Phase)
        </button>
      </div>
    </div>
  `;
}
