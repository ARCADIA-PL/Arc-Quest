package org.arcadia.arc_quest.core.time;

public interface CooldownRecord {

    long realTime();

    long gameTime();

    long dayTime();

    boolean exists();
}
