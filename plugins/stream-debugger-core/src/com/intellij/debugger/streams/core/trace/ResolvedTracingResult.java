// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace;

import com.intellij.debugger.streams.core.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface ResolvedTracingResult {
  @NotNull
  ResolvedStreamChain getResolvedChain();

  @NotNull
  StreamChain getSourceChain();

  boolean exceptionThrown();

  @NotNull
  TraceElement getResult();
}
