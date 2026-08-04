package org.arcadia.arc_quest.questmarker.internal.model;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

public sealed interface MarkerTarget permits MarkerTarget.Position, MarkerTarget.Entity {

    String dimension();

    double x();

    double y();

    double z();

    record Position(String dimension, double x, double y, double z) implements MarkerTarget {
    }

    record Entity(String dimension,
                  double x,
                  double y,
                  double z,
                  int runtimeEntityId,
                  String entityUuid,
                  String persistentGuid,
                  QuestMarkerData.EntityAttachPoint attachPoint) implements MarkerTarget {
    }
}
