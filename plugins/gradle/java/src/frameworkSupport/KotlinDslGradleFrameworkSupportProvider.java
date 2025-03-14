// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport;

import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class KotlinDslGradleFrameworkSupportProvider extends GradleFrameworkSupportProvider {
  public static final ExtensionPointName<GradleFrameworkSupportProvider> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.gradle.kotlinDslFrameworkSupport");
}
