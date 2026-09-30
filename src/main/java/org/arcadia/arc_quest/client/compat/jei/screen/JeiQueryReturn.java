package org.arcadia.arc_quest.client.compat.jei.screen;

/** Optional-dependency-free handoff for screens with transient panels. */
public interface JeiQueryReturn {
    void prepareJeiQuery();
    void cancelJeiQuery();
    void abandonJeiQuery();
}
