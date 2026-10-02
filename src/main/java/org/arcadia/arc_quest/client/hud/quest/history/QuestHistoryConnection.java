package org.arcadia.arc_quest.client.hud.quest.history;

/** Containment edges describe a sheet, while phase edges describe actual quest progression. */
record QuestHistoryConnection(String sourceId, String targetId, boolean containment) {}
