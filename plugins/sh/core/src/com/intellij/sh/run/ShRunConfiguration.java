// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.sh.psi.ShFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtilRt.notNullize;
import static com.intellij.sh.ShBundle.message;

public final class ShRunConfiguration extends LocatableConfigurationBase implements RefactoringListenerProvider {
  private static final @NonNls String TAG_PREFIX = "INDEPENDENT_";
  private static final @NonNls String SCRIPT_TEXT_TAG = "SCRIPT_TEXT";
  private static final @NonNls String SCRIPT_PATH_TAG = "SCRIPT_PATH";
  private static final @NonNls String SCRIPT_OPTIONS_TAG = "SCRIPT_OPTIONS";
  private static final @NonNls String SCRIPT_WORKING_DIRECTORY_TAG = "SCRIPT_WORKING_DIRECTORY";
  private static final @NonNls String INTERPRETER_PATH_TAG = "INTERPRETER_PATH";
  private static final @NonNls String INTERPRETER_OPTIONS_TAG = "INTERPRETER_OPTIONS";
  private static final @NonNls String EXECUTE_IN_TERMINAL_TAG = "EXECUTE_IN_TERMINAL";
  private static final @NonNls String EXECUTE_SCRIPT_FILE_TAG = "EXECUTE_SCRIPT_FILE";

  private String myScriptText = "";
  private String myScriptPath = "";
  private String myScriptOptions = "";
  private String myInterpreterPath = "";
  private String myInterpreterOptions = "";
  private String myScriptWorkingDirectory = "";
  private boolean myExecuteInTerminal = true;
  private boolean myExecuteScriptFile = true;
  private EnvironmentVariablesData myEnvData = EnvironmentVariablesData.DEFAULT;

  ShRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
    super(project, factory, name);
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ShRunConfigurationEditor(getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final var scriptPath = Path.of(myScriptPath);
    if (myExecuteScriptFile) {
      if (!Files.exists(scriptPath)) {
        throw new RuntimeConfigurationError(message("sh.run.script.not.found"));
      }
      if (StringUtil.isNotEmpty(myInterpreterPath) || !Files.isExecutable(scriptPath)) {
        final var interpreterPath = Path.of(myInterpreterPath);
        if (!Files.exists(interpreterPath)) {
          throw new RuntimeConfigurationError(message("sh.run.interpreter.not.found"));
        }
        if (!Files.isExecutable(interpreterPath)) {
          throw new RuntimeConfigurationError(message("sh.run.interpreter.should.be.executable"));
        }
      }
    }
    if (!Files.exists(Path.of(myScriptWorkingDirectory))) {
      throw new RuntimeConfigurationError(message("sh.run.working.dir.not.found"));
    }
  }

  @Override
  public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    return new ShRunConfigurationProfileState(environment.getProject(), this);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, SCRIPT_TEXT_TAG, myScriptText);
    writePathWithMetadata(element, myScriptPath, SCRIPT_PATH_TAG);
    JDOMExternalizerUtil.writeField(element, SCRIPT_OPTIONS_TAG, myScriptOptions);
    writePathWithMetadata(element, myScriptWorkingDirectory, SCRIPT_WORKING_DIRECTORY_TAG);
    writePathWithMetadata(element, myInterpreterPath, INTERPRETER_PATH_TAG);
    JDOMExternalizerUtil.writeField(element, INTERPRETER_OPTIONS_TAG, myInterpreterOptions);
    JDOMExternalizerUtil.writeField(element, EXECUTE_IN_TERMINAL_TAG, String.valueOf(myExecuteInTerminal));
    JDOMExternalizerUtil.writeField(element, EXECUTE_SCRIPT_FILE_TAG, String.valueOf(myExecuteScriptFile));
    myEnvData.writeExternal(element);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    myScriptText = readStringTagValue(element, SCRIPT_TEXT_TAG);
    myScriptPath = readPathWithMetadata(element, SCRIPT_PATH_TAG);
    myScriptOptions = readStringTagValue(element, SCRIPT_OPTIONS_TAG);
    myScriptWorkingDirectory = readPathWithMetadata(element, SCRIPT_WORKING_DIRECTORY_TAG);
    myInterpreterPath = readPathWithMetadata(element, INTERPRETER_PATH_TAG);
    myInterpreterOptions = readStringTagValue(element, INTERPRETER_OPTIONS_TAG);
    myExecuteInTerminal = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EXECUTE_IN_TERMINAL_TAG, Boolean.TRUE.toString()));
    myExecuteScriptFile = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EXECUTE_SCRIPT_FILE_TAG, Boolean.TRUE.toString()));
    myEnvData = EnvironmentVariablesData.readExternal(element);
  }

  @Override
  public @Nullable RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (StringUtil.isEmpty(myScriptPath) || !(element instanceof ShFile) || !myScriptPath.equals(getPathByElement(element))) return null;

    return new RefactoringElementAdapter() {
      @Override
      protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
        if (newElement instanceof ShFile) {
          setScriptPath(((ShFile)newElement).getVirtualFile().getPath());
        }
      }

      @Override
      public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
        elementRenamedOrMoved(newElement);
      }
    };
  }

  private static @Nullable String getPathByElement(@NotNull PsiElement element) {
    VirtualFile vfile = PsiUtilCore.getVirtualFile(element);
    if (vfile == null) return null;
    return vfile.getPath();
  }

  private static void writePathWithMetadata(@NotNull Element element, @NotNull String path, @NotNull String pathTag) {
    String systemIndependentPath = FileUtil.toSystemIndependentName(path);
    JDOMExternalizerUtil.writeField(element, TAG_PREFIX + pathTag, Boolean.toString(systemIndependentPath.equals(path)));
    JDOMExternalizerUtil.writeField(element, pathTag, systemIndependentPath);
  }

  private static String readPathWithMetadata(@NotNull Element element, @NotNull String pathTag) {
    return Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, TAG_PREFIX + pathTag))
           ? readStringTagValue(element, pathTag)
           : toSystemDependentName(readStringTagValue(element, pathTag));
  }

  private static @NotNull String readStringTagValue(@NotNull Element element, @NotNull String tagName) {
    return notNullize(JDOMExternalizerUtil.readField(element, tagName), "");
  }

  public String getScriptText() {
    return myScriptText;
  }

  public void setScriptText(String scriptText) {
    myScriptText = scriptText;
  }

  public String getScriptPath() {
    return myScriptPath;
  }

  public void setScriptPath(@NotNull String scriptPath) {
    myScriptPath = scriptPath.trim();
  }

  public String getScriptOptions() {
    return myScriptOptions;
  }

  public void setScriptOptions(@NotNull String scriptOptions) {
    myScriptOptions = scriptOptions.trim();
  }

  public String getScriptWorkingDirectory() {
    return myScriptWorkingDirectory;
  }

  public void setScriptWorkingDirectory(String scriptWorkingDirectory) {
    myScriptWorkingDirectory = scriptWorkingDirectory.trim();
  }

  public boolean isExecuteInTerminal() {
    return myExecuteInTerminal;
  }

  public void setExecuteInTerminal(boolean executeInTerminal) {
    myExecuteInTerminal = executeInTerminal;
  }

  public boolean isExecuteScriptFile() {
    return myExecuteScriptFile;
  }

  public void setExecuteScriptFile(boolean executeScriptFile) {
    myExecuteScriptFile = executeScriptFile;
  }

  public EnvironmentVariablesData getEnvData() {
    return myEnvData;
  }

  public void setEnvData(EnvironmentVariablesData envData) {
    myEnvData = envData;
  }

  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  public void setInterpreterPath(@NotNull String interpreterPath) {
    myInterpreterPath = interpreterPath.trim();
  }

  public String getInterpreterOptions() {
    return myInterpreterOptions;
  }

  public void setInterpreterOptions(@NotNull String interpreterOptions) {
    myInterpreterOptions = interpreterOptions.trim();
  }
}
