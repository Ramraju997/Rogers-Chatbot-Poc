package com.rogers.chatbot.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds files configured with relative paths whether the app is started from the
 * {@code backend} folder (mvn spring-boot:run) or from the project root (IDE run buttons).
 */
public final class PathResolver {

  private PathResolver() {}

  public static Path resolve(String configured) {
    Path path = Path.of(configured);
    if (path.isAbsolute() || Files.exists(path)) return path.toAbsolutePath();
    Path fromRoot = Path.of("backend").resolve(configured);
    return Files.exists(fromRoot) ? fromRoot.toAbsolutePath() : path.toAbsolutePath();
  }
}
