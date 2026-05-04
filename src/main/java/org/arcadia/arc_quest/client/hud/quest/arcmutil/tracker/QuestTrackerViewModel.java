package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import org.arcadia.arc_quest.mutil.viewmodel.ArcViewModelVersion;

import java.util.ArrayList;
import java.util.List;

public class QuestTrackerViewModel {
    public final ArcViewModelVersion version = new ArcViewModelVersion();
    public boolean visible;
    public boolean collectionQuest;
    public String questId = "";
    public String questTitle = "";
    public String phaseId = "";
    public String phaseName = "";
    public String phaseDescription = "";
    public int themeColor = 0xFF4FC3F7;
    public float panelReveal;
    public float panelSlide = 1f;
    public float wipeReveal = 1f;
    public float wipeAlpha = 1f;
    public float wipeDrift;
    public float uiScale = 1f;
    public int virtualScreenWidth;
    public int targetHeight;
    public int pushDownOffset;
    public final List<QuestTrackerPhaseViewModel> activePhases = new ArrayList<>();
    public final List<QuestTrackerObjectiveViewModel> objectives = new ArrayList<>();
    public final QuestTrackerCollectionViewModel collection = new QuestTrackerCollectionViewModel();

    public void markDirty() {
        version.markDirty();
    }

    public boolean isDirty() {
        return version.isDirty();
    }

    public void markClean() {
        version.markClean();
    }
}
