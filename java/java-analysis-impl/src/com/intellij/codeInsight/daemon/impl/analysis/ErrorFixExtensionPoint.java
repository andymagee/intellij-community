// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.diagnostic.PluginException;
import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.util.xmlb.annotations.Attribute;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ErrorFixExtensionPoint implements PluginAware {
  private static final ExtensionPointName<ErrorFixExtensionPoint> ERROR_FIX_EXTENSION_POINT =
    new ExtensionPointName<>("com.intellij.java.error.fix");

  @Attribute("errorCode")
  @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE)
  public String errorCode;

  @Attribute("implementationClass")
  public String implementationClass;

  private transient PluginDescriptor pluginDescriptor;

  private CommonIntentionAction instantiate(PsiElement context) {
    try {
      return ApplicationManager.getApplication().loadClass(implementationClass, pluginDescriptor)
        .asSubclass(CommonIntentionAction.class).getConstructor(PsiElement.class).newInstance(context);
    }
    catch (InvocationTargetException e) {
      if(e.getCause() instanceof ProcessCanceledException) {
        throw ((ProcessCanceledException)e.getCause());
      }
      throw new PluginException("Error instantiating quick-fix " + implementationClass + " (error code: " + errorCode + ")", e.getCause(), pluginDescriptor.getPluginId());
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
      throw new PluginException("Error instantiating quick-fix " + implementationClass + " (error code: " + errorCode + ")", e, pluginDescriptor.getPluginId());
    }
  }

  private static volatile Map<String, List<ErrorFixExtensionPoint>> ourCodeToFix;

  static {
    ERROR_FIX_EXTENSION_POINT.addChangeListener(() -> ourCodeToFix = null, null);
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  private static @NotNull Map<String, List<ErrorFixExtensionPoint>> getCodeToFixMap() {
    Map<String, List<ErrorFixExtensionPoint>> map = ourCodeToFix;
    if (map == null) {
      ourCodeToFix = map = StreamEx.of(ERROR_FIX_EXTENSION_POINT.getExtensionList()).groupingBy(fix -> fix.errorCode);
    }
    return map;
  }

  public static void registerFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                   @NotNull PsiElement context,
                                   @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String code) {
    List<ErrorFixExtensionPoint> fixes = getCodeToFixMap().getOrDefault(code, Collections.emptyList());
    for (ErrorFixExtensionPoint fix : fixes) {
      info.accept(fix.instantiate(context));
    }
  }
}
