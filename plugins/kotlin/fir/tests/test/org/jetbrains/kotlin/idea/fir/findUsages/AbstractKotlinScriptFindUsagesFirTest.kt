// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import org.jetbrains.kotlin.findUsages.AbstractKotlinScriptFindUsagesTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractKotlinScriptFindUsagesFirTest : AbstractKotlinScriptFindUsagesTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

}