// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.platform.navbar.frontend.NavBarRootPaneExtension;
import com.intellij.platform.navbar.frontend.ui.StaticNavBarPanel;
import com.intellij.platform.navbar.frontend.vm.NavBarVm;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
final class SelectInNavBarTarget extends SelectInTargetPsiWrapper implements DumbAware {
  public static final String NAV_BAR_ID = "NavBar";

  SelectInNavBarTarget(@NotNull Project project) {
    super(project);
  }

  @Override
  public @NonNls String getToolWindowId() {
    return NAV_BAR_ID;
  }

  @Override
  protected boolean canSelect(final PsiFileSystemItem file) {
    return UISettings.getInstance().getShowNavigationBar();
  }

  @Override
  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectInNavBar(false);
  }

  @Override
  protected void select(final PsiElement element, boolean requestFocus) {
    selectInNavBar(false);
  }

  public static void selectInNavBar(boolean showPopup) {
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      IdeFrame frame = IdeFrame.KEY.getData(context);
      if (frame == null) {
        return;
      }

      StatusBar statusBar = frame.getStatusBar();
      JComponent navBar = null;
      if (statusBar instanceof IdeStatusBarImpl) {
        navBar = ((IdeStatusBarImpl)statusBar).getWidgetComponent(IdeStatusBarImpl.NAVBAR_WIDGET_KEY);
      }
      if (navBar == null && frame instanceof IdeFrameEx) {
        navBar = ((IdeFrameEx)frame).getNorthExtension(IdeStatusBarImpl.NAVBAR_WIDGET_KEY);
      }

      if (navBar == null) {
        return;
      }

      Object panel = navBar.getClientProperty(NavBarRootPaneExtension.PANEL_KEY);
      if (panel instanceof StaticNavBarPanel navBarPanel) {
        NavBarVm vm = navBarPanel.getModel();
        if (vm != null) {
          vm.selectTail(showPopup);
        }
      }
    });
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.NAV_BAR_WEIGHT;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public String toString() {
    return IdeBundle.message("navigation.bar");
  }
}
