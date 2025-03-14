// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.actions.CreateFileAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class PackageUtil {
  private static final Logger LOG = Logger.getInstance(PackageUtil.class);

  /**
   * @param withInnerClasses whether to include inner classes.
   * @param scope the scope in which directories are searched.
   * @return all classes that are declared under a {@link PsiPackage}, including classes in sub packages.
   */
  public static @NotNull List<PsiClass> getClasses(@NotNull PsiPackage pkg, boolean withInnerClasses, @NotNull GlobalSearchScope scope) {
    ProgressManager.checkCanceled();
    return ReadAction.compute(() -> {
      List<PsiClass> classes = new ArrayList<>();
      for (PsiClass clazz : pkg.getClasses(scope)) {
        classes.add(clazz);
        if (withInnerClasses) {
          classes.addAll(new SmartList<>(clazz.getAllInnerClasses()));
        }
      }
      for (PsiPackage subPkg : pkg.getSubPackages(scope)) {
        classes.addAll(getClasses(subPkg, withInnerClasses, scope));
      }
      return classes;
    });
  }

  public static @Nullable PsiDirectory findPossiblePackageDirectoryInModule(@NotNull Module module, @NotNull String packageName) {
    return findPossiblePackageDirectoryInModule(module, packageName, true);
  }

  public static @Nullable PsiDirectory findPossiblePackageDirectoryInModule(
    @NotNull Module module,
    @NotNull String packageName,
    boolean preferNonGeneratedRoots
  ) {
    final Project project = module.getProject();
    PsiDirectory psiDirectory = null;
    if (!StringUtil.isEmptyOrSpaces(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(project, packageName);
      if (rootPackage != null) {
        final PsiDirectory[] psiDirectories = getPackageDirectoriesInModule(rootPackage, module);
        if (psiDirectories.length > 0) {
          psiDirectory = psiDirectories[0];

          // If we prefer to find a non-generated PsiDirectory for the given package name, search through all
          // the directories for the first dir not marked as generated and use that one instead
          if (preferNonGeneratedRoots && psiDirectories.length > 1) {
            for (PsiDirectory dir : psiDirectories) {
              if (!GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(dir.getVirtualFile(), project)) {
                psiDirectory = dir;
                break;
              }
            }
          }
        }
      }
    }
    if (psiDirectory == null) {
      if (checkSourceRootsConfigured(module)) {
        final List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
          if (directory != null) {
            psiDirectory = directory;
            break;
          }
        }
      }
    }
    return psiDirectory;
  }

  public static @Nullable PsiDirectory findOrCreateDirectoryForPackage(@NotNull Project project,
                                                                       @NotNull String packageName,
                                                                       @Nullable PsiDirectory baseDir,
                                                                       boolean askUserToCreate) throws IncorrectOperationException {
    PsiDirectory psiDirectory = null;

    if (!packageName.isEmpty()) {
      PsiPackage rootPackage = findLongestExistingPackage(project, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (!packageName.isEmpty()) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        PsiDirectory[] directories = rootPackage.getDirectories();
        psiDirectory = DirectoryChooserUtil.selectDirectory(project, directories, baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      PsiDirectory[] sourceDirectories = ProjectRootUtil.getSourceRootDirectories(project);
      psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (!restOfName.isEmpty()) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          int toCreate = Messages.showYesNoDialog(project,
                                                  JavaBundle.message("prompt.create.non.existing.package", packageName),
                                                  JavaBundle.message("title.package.not.found"),
                                                  Messages.getQuestionIcon());
          if (toCreate != Messages.YES) {
            return null;
          }
          askedToCreate = true;
        }
        psiDirectory = createSubdirectory(project, psiDirectory, name);
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory createSubdirectory(
    @NotNull Project project,
    @NotNull PsiDirectory oldDirectory,
    @NotNull String name
  ) throws IncorrectOperationException {
    final PsiDirectory[] psiDirectory = new PsiDirectory[1];
    final IncorrectOperationException[] exception = new IncorrectOperationException[1];

    CommandProcessor.getInstance().executeCommand(project, () -> psiDirectory[0] = ApplicationManager.getApplication().runWriteAction(
      (Computable<PsiDirectory>)() -> {
        try {
          return oldDirectory.createSubdirectory(name);
        }
        catch (IncorrectOperationException e) {
          exception[0] = e;
          return null;
        }
      }), JavaBundle.message("command.create.new.subdirectory"), null);

    if (exception[0] != null) throw exception[0];

    return psiDirectory[0];
  }

  public static @Nullable PsiDirectory findOrCreateDirectoryForPackage(@NotNull Module module,
                                                                       @NotNull String packageName,
                                                                       @Nullable PsiDirectory baseDir,
                                                                       boolean askUserToCreate) throws IncorrectOperationException {
    return findOrCreateDirectoryForPackage(module, packageName, baseDir, askUserToCreate, false);
  }

  public static @Nullable PsiDirectory findOrCreateDirectoryForPackage(@NotNull Module module,
                                                                       @NotNull String packageName,
                                                                       @Nullable PsiDirectory baseDir,
                                                                       boolean askUserToCreate,
                                                                       boolean filterSourceDirsForBaseTestDirectory) throws IncorrectOperationException {
    final Project project = module.getProject();
    PsiDirectory psiDirectory = null;
    if (!packageName.isEmpty()) {
      PsiPackage rootPackage = findLongestExistingPackage(module, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (!packageName.isEmpty()) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        PsiDirectory[] moduleDirectories = getPackageDirectoriesInModule(rootPackage, module);
        if (filterSourceDirsForBaseTestDirectory && baseDir != null) {
          moduleDirectories = filterSourceDirectories(project, baseDir, moduleDirectories);
        }
        psiDirectory = DirectoryChooserUtil.selectDirectory(project, moduleDirectories, baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      if (!checkSourceRootsConfigured(module, askUserToCreate)) return null;
      final List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
      List<PsiDirectory> directoryList = new ArrayList<>();
      for (VirtualFile sourceRoot : sourceRoots) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (directory != null) {
          directoryList.add(directory);
        }
      }
      PsiDirectory[] sourceDirectories = directoryList.toArray(PsiDirectory.EMPTY_ARRAY);
      psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (!restOfName.isEmpty()) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            int toCreate = Messages.showYesNoDialog(project,
                                                    JavaBundle.message("prompt.create.non.existing.package", packageName),
                                                    JavaBundle.message("title.package.not.found"),
                                                    Messages.getQuestionIcon());
            if (toCreate != Messages.YES) {
              return null;
            }
          }
          askedToCreate = true;
        }

        final PsiDirectory psiDirectory1 = psiDirectory;
        try {
          psiDirectory = WriteAction.compute(() -> psiDirectory1.createSubdirectory(name));
        }
        catch (IncorrectOperationException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory @NotNull [] filterSourceDirectories(
    @NotNull Project project,
    @NotNull PsiDirectory baseDir,
    PsiDirectory @NotNull [] moduleDirectories
  ) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInTestSourceContent(baseDir.getVirtualFile())) {
      List<PsiDirectory> result = new ArrayList<>();
      for (PsiDirectory moduleDirectory : moduleDirectories) {
        if (fileIndex.isInTestSourceContent(moduleDirectory.getVirtualFile())) {
          result.add(moduleDirectory);
        }
      }
      moduleDirectories = result.toArray(PsiDirectory.EMPTY_ARRAY);
    }
    return moduleDirectories;
  }

  private static PsiDirectory @NotNull [] getPackageDirectoriesInModule(@NotNull PsiPackage rootPackage, @NotNull Module module) {
    return rootPackage.getDirectories(GlobalSearchScope.moduleScope(module));
  }

  private static PsiPackage findLongestExistingPackage(@NotNull Project project, @NotNull String packageName) {
    String nameToMatch = packageName;
    while (true) {
      PsiPackage aPackage = JavaPsiFacade.getInstance(PsiManager.getInstance(project).getProject()).findPackage(nameToMatch);
      if (aPackage != null && isWritablePackage(aPackage)) return aPackage;
      int lastDotIndex = nameToMatch.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      }
      else {
        return null;
      }
    }
  }

  private static boolean isWritablePackage(@NotNull PsiPackage aPackage) {
    PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (directory.isValid() && directory.isWritable()) {
        return true;
      }
    }
    return false;
  }

  private static PsiDirectory getWritableModuleDirectory(@NotNull Module module, @NotNull Query<? extends VirtualFile> vFiles) {
    PsiManager manager = PsiManager.getInstance(module.getProject());
    for (VirtualFile vFile : vFiles.asIterable()) {
      if (ModuleUtilCore.findModuleForFile(vFile, module.getProject()) != module) continue;
      PsiDirectory directory = manager.findDirectory(vFile);
      if (directory != null && directory.isValid() && directory.isWritable()) {
        return directory;
      }
    }
    return null;
  }

  private static PsiPackage findLongestExistingPackage(@NotNull Module module, @NotNull String packageName) {
    String nameToMatch = packageName;
    while (true) {
      Query<VirtualFile> vFiles = ModulePackageIndex.getInstance(module).getDirsByPackageName(nameToMatch, false);
      PsiDirectory directory = getWritableModuleDirectory(module, vFiles);
      if (directory != null) return JavaDirectoryService.getInstance().getPackage(directory);

      int lastDotIndex = nameToMatch.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      }
      else {
        return null;
      }
    }
  }

  private static String getLeftPart(@NotNull String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(0, index) : packageName;
  }

  private static String cutLeftPart(@NotNull String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(index + 1) : "";
  }

  public static boolean checkSourceRootsConfigured(@NotNull Module module) {
    return checkSourceRootsConfigured(module, true);
  }

  public static boolean checkSourceRootsConfigured(@NotNull Module module, boolean askUserToSetupSourceRoots) {
    List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    if (sourceRoots.isEmpty()) {
      if (!askUserToSetupSourceRoots) {
        return false;
      }

      Project project = module.getProject();
      Messages.showErrorDialog(project,
                               ProjectBundle.message("module.source.roots.not.configured.error", module.getName()),
                               ProjectBundle.message("module.source.roots.not.configured.title"));

      ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(module.getName(), CommonContentEntriesEditor.getName());

      sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
      if (sourceRoots.isEmpty()) return false;
    }
    return true;
  }

  public static @NotNull PsiDirectory findOrCreateSubdirectory(@NotNull PsiDirectory directory, @NotNull String directoryName) {
    return CreateFileAction.findOrCreateSubdirectory(directory, directoryName);
  }

  public static boolean isPackageInfoFile(@Nullable PsiElement element) {
    return element instanceof PsiJavaFile && PsiPackage.PACKAGE_INFO_FILE.equals(((PsiJavaFile)element).getName());
  }
}
