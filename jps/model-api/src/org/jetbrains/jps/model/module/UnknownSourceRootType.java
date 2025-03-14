// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UnknownSourceRootType extends JpsElementTypeBase<UnknownSourceRootTypeProperties<?>> implements JpsModuleSourceRootType<UnknownSourceRootTypeProperties<?>> {
  private static final Map<String, UnknownSourceRootType> ourTypeNameToInstanceMap = new ConcurrentHashMap<>();
  private final String myUnknownTypeId;

  private UnknownSourceRootType(String unknownTypeId) {
    myUnknownTypeId = unknownTypeId;
  }

  public String getUnknownTypeId() {
    return myUnknownTypeId;
  }

  @Override
  public @NotNull UnknownSourceRootTypeProperties<?> createDefaultProperties() {
    return new UnknownSourceRootTypeProperties<>(null);
  }

  public static UnknownSourceRootType getInstance(String typeId) {
    return ourTypeNameToInstanceMap.computeIfAbsent(typeId, id -> new UnknownSourceRootType(id));
  }
}
