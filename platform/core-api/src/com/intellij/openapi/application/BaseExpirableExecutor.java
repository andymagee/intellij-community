// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Callable;

/**
 * DO NOT USE
 *
 * @see ExpirableExecutor
 * @see AppUIExecutor
 * @deprecated use coroutines and their cancellation mechanism instead
 */
@Deprecated
public interface BaseExpirableExecutor<E extends BaseExpirableExecutor<E>> {
  /**
   * @return an executor that no longer invokes the given runnable after the supplied Disposable is disposed
   */
  @NotNull
  @Contract(pure = true)
  E expireWith(@NotNull Disposable parentDisposable);

  /**
   * Schedule execution of the given task.
   */
  void execute(@NotNull Runnable command);

  /**
   * Schedule the given task's execution and return a Promise that allows to get the result when the task is complete,
   * or cancel the task if it's no longer needed.
   */
  <T> CancellablePromise<T> submit(@NotNull Callable<T> task);

  /**
   * Schedule the given task's execution and return a Promise that allows to check if the task is complete,
   * or cancel the task if it's no longer needed.
   */
  CancellablePromise<?> submit(@NotNull Runnable task);
}
