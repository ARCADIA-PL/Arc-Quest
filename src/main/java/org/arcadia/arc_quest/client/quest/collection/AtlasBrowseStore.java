package org.arcadia.arc_quest.client.quest.collection;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Scoped browser preferences. Disk access is restricted to explicit lifecycle/input calls. */
public final class AtlasBrowseStore {
  private static final Properties VALUES = new Properties();
  private static Path file;

  private AtlasBrowseStore() {}

  public static synchronized void open() {
    Path scope = CollectionFavoriteScope.currentFile();
    Path next =
        scope == null ? null : scope.resolveSibling(scope.getFileName() + ".atlas.properties");
    if (Objects.equals(file, next)) return;
    file = next;
    VALUES.clear();
    if (file != null && Files.isRegularFile(file))
      try (InputStream in = Files.newInputStream(file)) {
        VALUES.load(in);
      } catch (IOException ignored) {
      }
  }

  public static synchronized boolean has(String key) {
    return VALUES.containsKey(key);
  }

  public static synchronized String read(String key, String fallback) {
    return VALUES.getProperty(key, fallback);
  }

  public static synchronized AtlasCamera camera(String key) {
    AtlasCamera camera = new AtlasCamera();
    try {
      String[] parts = read(key, "").split(",");
      if (parts.length == 3) {
        camera.x = Double.parseDouble(parts[0]);
        camera.y = Double.parseDouble(parts[1]);
        camera.zoom = Math.max(.65, Math.min(2.4, Double.parseDouble(parts[2])));
        camera.advance(0);
      }
    } catch (NumberFormatException ignored) {
    }
    return camera;
  }

  public static synchronized void put(String key, String value) {
    VALUES.setProperty(key, value);
  }

  public static synchronized void putCamera(String key, AtlasCamera c) {
    put(key, c.destinationX() + "," + c.destinationY() + "," + c.destinationZoom());
  }

  public static synchronized void save() {
    if (file == null) return;
    try {
      Files.createDirectories(file.getParent());
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      try (OutputStream out = Files.newOutputStream(temporary)) {
        VALUES.store(out, "ArcQ atlas local browser preferences");
      }
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ignored) {
    }
  }
}
