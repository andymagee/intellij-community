// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.IdeView;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.project.TestProjectManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixture;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixturePathProvider;
import com.intellij.util.PathUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates new project for each test.
 */
@SuppressWarnings("TestOnlyProblems")
final class HeavyIdeaTestFixtureImpl extends BaseFixture implements HeavyIdeaTestFixture {
  private Project myProject;
  private volatile Module myModule;
  private final Set<Path> myFilesToDelete = new HashSet<>();
  private final Set<ModuleFixtureBuilder<?>> myModuleFixtureBuilders = new LinkedHashSet<>();
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;
  private final String mySanitizedName;
  private @Nullable HeavyIdeaTestFixturePathProvider myProjectPathProvider;
  private final boolean myIsDirectoryBasedProject;
  private SdkLeakTracker mySdkLeakTracker;

  private AccessToken projectTracker;

  HeavyIdeaTestFixtureImpl(@NotNull String name,
                           @Nullable HeavyIdeaTestFixturePathProvider projectPathProvider,
                           boolean isDirectoryBasedProject) {
    mySanitizedName = FileUtil.sanitizeFileName(name, false);
    myProjectPathProvider = projectPathProvider;
    myIsDirectoryBasedProject = isDirectoryBasedProject;
  }

  void addModuleFixtureBuilder(ModuleFixtureBuilder<?> builder) {
    myModuleFixtureBuilders.add(builder);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initApplication();
    projectTracker = ((TestProjectManager)ProjectManager.getInstance()).startTracking();

    setUpProject();

    EncodingManager.getInstance(); // adds listeners
    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();
    InjectedLanguageManagerImpl.pushInjectors(getProject());
    mySdkLeakTracker = new SdkLeakTracker();
  }

  @Override
  public void tearDown() {
    List<ThrowableRunnable<?>> actions = new ArrayList<>();

    if (myProject != null) {
      Project project = myProject;
      actions.add(() -> {
        TestApplicationManager.tearDownProjectAndApp(myProject);
        myProject = null;
      });

      for (ModuleFixtureBuilder<?> moduleFixtureBuilder : myModuleFixtureBuilders) {
        actions.add(() -> moduleFixtureBuilder.getFixture().tearDown());
      }

      actions.add(() -> InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project));
    }

    JarFileSystemImpl.cleanupForNextTest();

    for (Path fileToDelete : myFilesToDelete) {
      actions.add(() -> {
        List<IOException> errors;
        try (Stream<Path> stream = Files.walk(fileToDelete)) {
          errors = stream
            .sorted(Comparator.reverseOrder())
            .map(x -> {
              try {
                Files.delete(x);
                return null;
              }
              catch (IOException e) {
                return e;
              }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        }
        catch (NoSuchFileException ignore) {
          errors = Collections.emptyList();
        }
        CompoundRuntimeException.throwIfNotEmpty(errors);
      });
    }

    actions.add(() -> {
      AccessToken projectTracker = this.projectTracker;
      if (projectTracker != null) {
        this.projectTracker = null;
        projectTracker.finish();
      }
    });
    actions.add(() -> super.tearDown());
    actions.add(() -> {
      if (myEditorListenerTracker != null) {
        myEditorListenerTracker.checkListenersLeak();
      }
    });
    actions.add(() -> {
      if (myThreadTracker != null) {
        VfsTestUtil.waitForFileWatcher();
        myThreadTracker.checkLeak();
      }
    });
    actions.add(() -> {
      HeavyIdeaTestFixturePathProvider provider = myProjectPathProvider;
      if (provider != null) {
        provider.afterTest(mySanitizedName);
      }
    });
    actions.add(() -> LightPlatformTestCase.checkEditorsReleased());
    actions.add(() -> {
      if (mySdkLeakTracker != null) {
        mySdkLeakTracker.checkForJdkTableLeaks();
      }
    });
    // project is disposed by now, no point in passing it
    actions.add(() -> HeavyPlatformTestCase.cleanupApplicationCaches(null));

    new RunAll(actions).run();
  }

  private void setUpProject() throws Exception {
    OpenProjectTask options = OpenProjectTaskBuilderKt.createTestOpenProjectOptions(true, project -> {
      project.getMessageBus().simpleConnect().subscribe(ModuleListener.TOPIC, new ModuleListener() {
        @Override
        public void moduleAdded(@NotNull Project __, @NotNull Module module) {
          if (myModule == null || myModule.isDisposed()) {
            myModule = module;
          }
        }
      });
    });
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    myProject = Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(generateProjectPath(), options));
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    EdtTestUtil.runInEdtAndWait(() -> {
      for (ModuleFixtureBuilder<?> moduleFixtureBuilder : myModuleFixtureBuilders) {
        moduleFixtureBuilder.getFixture().setUp();
      }

      LightPlatformTestCase.clearUncommittedDocuments(myProject);
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    });
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
  }

  private @NotNull Path generateProjectPath() {
    Path tempDirectory;
    HeavyIdeaTestFixturePathProvider pathProvider = myProjectPathProvider;
    if (pathProvider == null) {
      pathProvider = myProjectPathProvider = ApplicationManager.getApplication().getService(HeavyIdeaTestFixturePathProvider.class);
    }
    Path projectPath = null;
    if (pathProvider != null) {
      projectPath = pathProvider.get(mySanitizedName, getTestRootDisposable());
    }
    if (projectPath == null) {
      tempDirectory = TemporaryDirectory.generateTemporaryPath(mySanitizedName);
      myFilesToDelete.add(tempDirectory);
    }
    else {
      tempDirectory = projectPath;
    }
    return tempDirectory.resolve(mySanitizedName + (myIsDirectoryBasedProject ? "" : ProjectFileType.DOT_DEFAULT_EXTENSION));
  }

  private void initApplication() {
    TestApplicationManager.getInstance().setDataProvider(new MyDataProvider());
  }

  @Override
  public Project getProject() {
    Assert.assertNotNull("setUp() should be called first", myProject);
    return myProject;
  }

  @Override
  public Module getModule() {
    return myModule;
  }

  private final class MyDataProvider implements DataProvider {
    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      else if (CommonDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
        Project project = myProject;
        return project == null || project.isDisposed() ? null : FileEditorManager.getInstance(project).getSelectedTextEditor();
      }
      if (LangDataKeys.IDE_VIEW.is(dataId)) {
        Project project = myProject;
        VirtualFile[] contentRoots = project == null ? VirtualFile.EMPTY_ARRAY : ProjectRootManager.getInstance(myProject).getContentRoots();
        if (contentRoots.length > 0 && project != null) {
          return new IdeView() {
            @Override
            public PsiDirectory @NotNull [] getDirectories() {
              return new PsiDirectory[]{PsiManager.getInstance(project).findDirectory(contentRoots[0])};
            }

            @Override
            public PsiDirectory getOrChooseDirectory() {
              return PsiManager.getInstance(project).findDirectory(contentRoots[0]);
            }
          };
        }
      }
      return null;
    }
  }

  @Override
  public PsiFile addFileToProject(@NotNull @NonNls String rootPath, final @NotNull @NonNls String relativePath, final @NotNull @NonNls String fileText) throws IOException {
    final VirtualFile dir = VfsUtil.createDirectories(rootPath + "/" + PathUtil.getParentPath(relativePath));

    final VirtualFile[] virtualFile = new VirtualFile[1];
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      virtualFile[0] = dir.createChildData(this, StringUtil.getShortName(relativePath, '/'));
      VfsUtil.saveText(virtualFile[0], fileText);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    return ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(virtualFile[0]));
  }
}
