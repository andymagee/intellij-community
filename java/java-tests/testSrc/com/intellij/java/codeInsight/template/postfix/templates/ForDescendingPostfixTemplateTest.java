/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.refactoring.JavaRefactoringSettings;
import org.jetbrains.annotations.NotNull;

public class ForDescendingPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "forr";
  }

  public void testIntArray() {
    doTest();
  }
  
  public void testIntArrayVar() {
    JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
    instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
    }
  }

  public void testByteNumber() {
    doTest();
  }

  public void testBoxedIntegerArray() {
    doTest();
  }

  public void testBoxedLongArray() {
    doTest();
  }
}
