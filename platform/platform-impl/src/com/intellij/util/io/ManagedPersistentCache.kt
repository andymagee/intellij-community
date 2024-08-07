// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
open class ManagedPersistentCache<K, V>(
  private val name: String,
  private val mapBuilder: PersistentMapBuilder<K, V>,
  closeOnAppShutdown: Boolean = true,
  cleanDirOnFailure: Boolean = !ApplicationManager.getApplication().isUnitTestMode,
) : ManagedCache<K, V> {
  private val persistentMap: AtomicReference<PersistentMapBase<K, V>> = AtomicReference()
  private val errorCounter: AtomicInteger = AtomicInteger()

  init {
    persistentMap.set(createPersistentMap(closeOnAppShutdown, cleanDirOnFailure))
  }

  companion object {
    private const val IO_ERRORS_THRESHOLD: Int = 20
    private const val CREATE_ATTEMPT_COUNT: Int = 5
    private const val CREATE_ATTEMPT_DELAY_MS: Long = 10
    private val LOG: Logger = logger<ManagedPersistentCache<*, *>>()
    private val TRACKED_CACHES: MutableSet<ManagedPersistentCache<*, *>> = ConcurrentCollectionFactory.createConcurrentSet()
    init {
      ShutDownTracker.getInstance().registerCacheShutdownTask {
        TRACKED_CACHES.forEach { cache -> cache.close(isAppShutdown=true) }
      }
    }
  }

  override operator fun get(key: K): V? {
    ThreadingAssertions.assertBackgroundThread()
    return performSafe("get") { map ->
      map.get(key)
    }
  }

  override operator fun set(key: K, value: V) {
    ThreadingAssertions.assertBackgroundThread()
    performSafe("set") { map ->
      map.put(key, value)
    }
  }

  override fun remove(key: K) {
    ThreadingAssertions.assertBackgroundThread()
    performSafe("remove") { map ->
      map.remove(key)
    }
  }

  override fun force() {
    ThreadingAssertions.assertBackgroundThread()
    performSafe("force") { map ->
      map.force()
    }
  }

  override fun close() {
    ThreadingAssertions.assertBackgroundThread()
    close(isAppShutdown=false)
  }

  override fun isClosed(): Boolean {
    ThreadingAssertions.assertBackgroundThread()
    val persistentMap = persistentMap.get()
    if (persistentMap == null) {
      return true
    }
    return persistentMap.isClosed
  }

  override suspend fun forceOnTimer(periodMs: Long) {
    ThreadingAssertions.assertBackgroundThread()
    coroutineScope {
      launch {
        delay(periodMs)
        while (isActive) {
          force()
          delay(periodMs)
        }
      }
      val watcher = LowMemoryWatcher.register {
        launch {
          force()
        }
      }
      awaitCancellationAndInvoke {
        watcher.stop()
        close()
      }
    }
  }

  private fun close(isAppShutdown: Boolean) {
    val persistentMap = persistentMap.getAndSet(null)
    if (persistentMap == null) {
      return
    }
    if (!isAppShutdown) {
      TRACKED_CACHES.remove(this)
    }
    close(persistentMap)
  }

  private fun close(persistentMap: PersistentMapBase<K, V>) {
    if (LOG.isDebugEnabled) {
      LOG.info("closing persistent map $name with ${persistentMap.keysCount()} elements")
    }
    else {
      LOG.info("closing persistent map $name")
    }

    try {
      persistentMap.force() // IJPL-157442 prevent data loss
      persistentMap.close()
    } catch (e: Exception) {
      LOG.warn("error closing persistent map $name", e)
    }
  }

  private fun createPersistentMap(
    closeOnAppShutdown: Boolean,
    cleanDirOnFailure: Boolean,
  ): PersistentMapBase<K, V>? {
    var map: PersistentMapBase<K, V>? = null
    var exception: Exception? = null
    for (attempt in 0 until CREATE_ATTEMPT_COUNT) {
      if (attempt > 1) {
        Thread.sleep(CREATE_ATTEMPT_DELAY_MS)
      }
      try {
        map = PersistentMapImpl(mapBuilder)
        break
      } catch (e: VersionUpdatedException) {
        exception = e
        LOG.info("$name ${e.message}")
      } catch (e: IOException) {
        exception = e
        if (attempt != CREATE_ATTEMPT_COUNT - 1) {
          LOG.warn("error creating persistent map $name, attempt $attempt", e)
        }
      } catch (e: Exception) {
        // e.g., storage is already registered
        exception = e
        break
      }
      if (cleanDirOnFailure) {
        IOUtil.deleteAllFilesStartingWith(mapBuilder.file)
      } else {
        // IJPL-149672 do not delete files in test mode
        return null
      }
    }
    if (map == null) {
      LOG.error("cannot create persistent map $name", exception)
      return null
    }
    LOG.info("created persistent map $name with size ${map.keysCount()}")
    if (closeOnAppShutdown) {
      val added = TRACKED_CACHES.add(this)
      if (!added) {
        LOG.error(
          "Probably the project was not disposed properly before reopening. " +
          "Persistent map $name has already been registered. " +
          "List of registered maps: $TRACKED_CACHES"
        )
        close(map)
        return null
      }
    }
    return map
  }

  private fun <T> performSafe(opName: String, operation: (PersistentMapBase<K, V>) -> T?): T? {
    val persistentMap = persistentMap.get()
    if (persistentMap == null) {
      return null
    }
    try {
      val result = operation.invoke(persistentMap)
      errorCounter.set(0)
      return result
    } catch (e: IOException) {
      LOG.warn("error performing $opName by persistent map $name", e)
      val count = errorCounter.incrementAndGet()
      if (count > IO_ERRORS_THRESHOLD) {
        LOG.warn("error count exceeds the threshold, closing persistent map $name")
        this.persistentMap.compareAndSet(persistentMap, null)
        close(persistentMap)
      }
      return null
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ManagedPersistentCache<*, *>) return false
    if (name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun toString(): String {
    val persistentMap = persistentMap.get()
    val size = persistentMap?.keysCount() ?: -1
    return "ManagedCache($name, size=$size)"
  }
}
