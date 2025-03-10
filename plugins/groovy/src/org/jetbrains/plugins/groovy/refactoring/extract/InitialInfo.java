// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class InitialInfo implements ExtractInfoHelper {
  private final ParameterInfo[] myParameterInfos;
  private final VariableInfo[] myOutputNames;
  private final PsiType myOutputType;
  private final PsiElement[] myInnerElements;
  private final Project myProject;
  private final GrStatement[] myStatements;
  private final boolean myHasReturnValue;
  private final String[] myArgumentNames;
  private final StringPartInfo myStringPartInfo;
  private final GrVariable myVariable;

  public InitialInfo(VariableInfo[] inputInfos,
                     VariableInfo[] outputInfos,
                     PsiElement[] innerElements,
                     GrStatement[] statements,
                     ArrayList<GrStatement> returnStatements,
                     StringPartInfo stringPartInfo,
                     Project project, GrVariable variable) {
    myInnerElements = innerElements;
    myStatements = statements;
    myOutputNames = outputInfos;
    myStringPartInfo = stringPartInfo;
    myVariable = variable;

    myHasReturnValue = ContainerUtil.find(returnStatements, statement -> statement instanceof GrReturnStatement && ((GrReturnStatement)statement).getReturnValue() != null) != null;

    assert myStringPartInfo != null || myStatements.length > 0;
    myProject = project;

    myParameterInfos = new ParameterInfo[inputInfos.length];
    myArgumentNames = new String[inputInfos.length];
    for (int i = 0; i < inputInfos.length; i++) {
      VariableInfo info = inputInfos[i];
      PsiType type = info.getType();
      myParameterInfos[i] = new ParameterInfo(info.getName(), i, type);
      myArgumentNames[i] = info.getName();
    }

    PsiType outputType = inferOutputType(outputInfos, statements, returnStatements, myHasReturnValue, stringPartInfo);
    myOutputType = outputType != null ? outputType : PsiTypes.voidType();
  }

  private @Nullable PsiType inferOutputType(VariableInfo[] outputInfos,
                                            GrStatement[] statements,
                                            ArrayList<GrStatement> returnStatements,
                                            boolean hasReturnValue,
                                            StringPartInfo stringPartInfo) {
    if (stringPartInfo != null) {
      return stringPartInfo.getLiteral().getType();
    }
    PsiType outputType = PsiTypes.voidType();
    if (outputInfos.length > 0) {
      if (outputInfos.length == 1) {
        outputType = outputInfos[0].getType();
      }
      else {
        outputType = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, getContext());
      }
    }
    else if (ExtractUtil.isSingleExpression(statements)) {
      outputType = ((GrExpression)statements[0]).getType();
    }
    else if (hasReturnValue) {
      assert !returnStatements.isEmpty();
      List<PsiType> types = new ArrayList<>(returnStatements.size());
      for (GrStatement statement : returnStatements) {
        if (statement instanceof GrReturnStatement) {
          GrExpression returnValue = ((GrReturnStatement)statement).getReturnValue();
          if (returnValue != null) {
            types.add(returnValue.getType());
          }
        }
        else if (statement instanceof GrExpression) {
          types.add(((GrExpression)statement).getType());
        }
      }
      outputType = TypesUtil.getLeastUpperBoundNullable(types, getContext().getManager());
    }

    return outputType;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public ParameterInfo @NotNull [] getParameterInfos() {
    return myParameterInfos;
  }

  @Override
  public VariableInfo @NotNull [] getOutputVariableInfos() {
    return myOutputNames;
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  @Override
  public String @NotNull [] getArgumentNames() {
    return myArgumentNames;
  }

  @Override
  public @NotNull PsiType getOutputType() {
    return myOutputType;
  }

  @Override
  public PsiElement @NotNull [] getInnerElements() {
    return myInnerElements;
  }

  @Override
  public GrStatement @NotNull [] getStatements() {
    return myStatements;
  }

  @Override
  public boolean hasReturnValue() {
    return myHasReturnValue;
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getContext() {
    return myStatements.length > 0 ? myStatements[0] : myStringPartInfo.getLiteral();
  }

  @Override
  public boolean isForceReturn() {
    return false;
  }

  @Override
  public @Nullable StringPartInfo getStringPartInfo() {
    return myStringPartInfo;
  }

  @Override
  public @Nullable GrVariable getVar() {
    return myVariable;
  }
}
