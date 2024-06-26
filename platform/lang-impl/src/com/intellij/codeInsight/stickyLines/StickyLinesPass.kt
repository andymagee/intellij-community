// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.stickyLines

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.stickyLines.StickyLineInfo
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.util.concurrent.atomic.AtomicBoolean

internal class StickyLinesPass(
  project: Project,
  document: Document,
  private val vFile: VirtualFile,
  private val psiFile: PsiFile,
) : TextEditorHighlightingPass(project, document), DumbAware {

  private var updater: Runnable? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    updater = CachedValuesManager.getCachedValue(
      psiFile,
      StickyLinesCollector.CACHED_LINES_KEY,
      getValueProvider(myProject, myDocument, vFile, dependency = psiFile)
    )
  }

  override fun doApplyInformationToEditor() {
    updater?.run()
    updater = null
  }

  companion object {
    private fun getValueProvider(
      project: Project,
      document: Document,
      vFile: VirtualFile,
      dependency: Any,
    ): CachedValueProvider<Runnable> {
      return CachedValueProvider {
        val collector = StickyLinesCollector(project, document)
        val infos: Set<StickyLineInfo> = collector.collectLines(vFile)
        val alreadyApplied = AtomicBoolean()
        val applier = Runnable {
          if (alreadyApplied.compareAndSet(false, true)) {
            collector.applyLines(infos)
          }
        }
        CachedValueProvider.Result.create(applier, dependency)
      }
    }
  }
}
