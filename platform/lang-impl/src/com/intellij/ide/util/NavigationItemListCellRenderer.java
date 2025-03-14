// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.lang.LangBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

public class NavigationItemListCellRenderer extends JPanel implements ListCellRenderer<Object> {

  public NavigationItemListCellRenderer() {
    super(new BorderLayout());
  }

  @Override
  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    removeAll();

    final boolean hasRightRenderer = UISettings.getInstance().getShowIconInQuickNavigation();
    final ModuleRendererFactory factory = ModuleRendererFactory.findInstance(value);
    String accessibleName = "";

    final LeftRenderer left = new LeftRenderer(!hasRightRenderer || !factory.rendersLocationString(), MatcherHolder.getAssociatedMatcher(list));
    final Component leftCellRendererComponent = left.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    final Color listBg = leftCellRendererComponent.getBackground();
    ((JComponent) leftCellRendererComponent).setOpaque(false);
    add(leftCellRendererComponent, BorderLayout.WEST);
    accessibleName += leftCellRendererComponent.getAccessibleContext().getAccessibleName();

    setBackground(isSelected ? UIUtil.getListSelectionBackground(true) : listBg);

    if  (hasRightRenderer){
      final DefaultListCellRenderer moduleRenderer = factory.getModuleRenderer();

      final Component rightCellRendererComponent =
        moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      ((JComponent)rightCellRendererComponent).setOpaque(false);
      rightCellRendererComponent.setBackground(listBg);
      add(rightCellRendererComponent, BorderLayout.EAST);
      accessibleName += " " + rightCellRendererComponent.getAccessibleContext().getAccessibleName();
      final JPanel spacer = new NonOpaquePanel();

      final Dimension size = rightCellRendererComponent.getSize();
      spacer.setSize(new Dimension((int)(size.width * 0.015 + leftCellRendererComponent.getSize().width * 0.015), size.height));
      spacer.setBackground(isSelected ? UIUtil.getListSelectionBackground(true) : listBg);
      add(spacer, BorderLayout.CENTER);
    }
    getAccessibleContext().setAccessibleName(accessibleName);
    return this;
  }

  private static final class LeftRenderer extends ColoredListCellRenderer {
    public final boolean myRenderLocation;
    private final Matcher myMatcher;

    LeftRenderer(boolean renderLocation, Matcher matcher) {
      myRenderLocation = renderLocation;
      myMatcher = matcher;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         Object value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      Color bgColor = UIUtil.getListBackground();

      if (value instanceof PsiElement && !((PsiElement)value).isValid()) {
        setIcon(IconUtil.getEmptyIcon(false));
        append(LangBundle.message("label.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof NavigationItem item) {
        Color color = list.getForeground();

        String name;
        Icon icon;
        ItemPresentation presentation;
        TextAttributes textAttributes;
        boolean isProblemFile = false;
        try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162822")) {
          presentation = item.getPresentation();
          assert presentation != null :
            "PSI elements displayed in choose by name lists must return a non-null value from getPresentation(): element " +
            item +
            ", class " +
            item.getClass().getName();
          name = presentation.getPresentableText();
          assert name != null :
            "PSI elements displayed in choose by name lists must return a non-null value from getPresentation().getPresentableName: element " +
            item +
            ", class " +
            item.getClass().getName();

          PsiElement psiElement = PSIRenderingUtils.getPsiElement(item);
          if (psiElement != null && psiElement.isValid()) {
            Project project = psiElement.getProject();
            VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
            isProblemFile = virtualFile != null && WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);

            Color fileColor = virtualFile == null ? null : getFileBackgroundColor(project, virtualFile);
            if (fileColor != null) {
              bgColor = fileColor;
            }
          }
          FileStatus status = NavigationItemFileStatus.get(item);
          if (status != FileStatus.NOT_CHANGED) {
            color = status.getColor();
          }

          textAttributes = NodeRenderer.getSimpleTextAttributes(presentation).toTextAttributes();
          icon = presentation.getIcon(false);
        }

        if (isProblemFile) {
          textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
          textAttributes.setEffectColor(JBColor.red);
        }
        textAttributes.setForegroundColor(color);
        SimpleTextAttributes nameAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
        SpeedSearchUtil.appendColoredFragmentForMatcher(name,  this, nameAttributes, myMatcher, bgColor, selected);
        setIcon(icon);

        if (myRenderLocation) {
          String containerText = presentation.getLocationString();

          if (containerText != null && !containerText.isEmpty()) {
            append(" " + containerText, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
          }
        }
      }
      else {
        setIcon(IconUtil.getEmptyIcon(false));
        @NlsSafe String text = value == null ? "" : value.toString();
        append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
      setPaintFocusBorder(false);
      setBackground(selected ? UIUtil.getListSelectionBackground(true) : bgColor);
    }
  }
}
