// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlEnumValue;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;

public enum Depth {

  @XmlEnumValue("") UNKNOWN("unknown"),
  @XmlEnumValue("infinity") INFINITY("infinity"),
  @XmlEnumValue("immediates") IMMEDIATES("immediates"),
  @XmlEnumValue("files") FILES("files"),
  @XmlEnumValue("empty") EMPTY("empty"),
  @XmlEnumValue("exclude") EXCLUDE("exclude");

  private static final @NotNull Map<String, Depth> ourAllDepths = new HashMap<>();

  static {
    for (Depth action : values()) {
      register(action);
    }
  }

  private final @NonNls @NotNull String myName;

  Depth(@NonNls @NotNull String name) {
    myName = name;
  }

  public @NonNls @NotNull String getName() {
    return myName;
  }

  public @Nls @NotNull String getDisplayName() {
    return message(switch (this) {
      case INFINITY -> "label.depth.infinity";
      case IMMEDIATES -> "label.depth.immediates";
      case FILES -> "label.depth.files";
      case EMPTY -> "label.depth.empty";
      case EXCLUDE -> "label.depth.exclude";
      default -> "label.depth.unknown";
    });
  }

  @Override
  public String toString() {
    return myName;
  }

  private static void register(@NotNull Depth depth) {
    ourAllDepths.put(depth.myName, depth);
  }

  public static @NotNull Depth from(@NonNls @NotNull String depthName) {
    Depth result = ourAllDepths.get(depthName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown depth " + depthName);
    }

    return result;
  }

  public static @NotNull Depth allOrFiles(boolean recursive) {
    return recursive ? INFINITY : FILES;
  }

  public static @NotNull Depth allOrEmpty(boolean recursive) {
    return recursive ? INFINITY : EMPTY;
  }

  public static boolean isRecursive(@Nullable Depth depth) {
    return depth == null || depth == INFINITY || depth == UNKNOWN;
  }
}
