// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModifiableModuleModelBridge : ModifiableModuleModel {
  fun prepareForCommit()
  fun collectChanges(): MutableEntityStorage
}