/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea;

import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.R_CLASS;

public class AndroidPsiUtils {
  /** Type of resource reference: R.type.name or android.R.type.name or neither */
  public enum ResourceReferenceType { NONE, APP, FRAMEWORK }

  /**
   * Returns true if the given PsiElement is a reference to an Android Resource.
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  public static boolean isResourceReference(@NotNull PsiElement element) {
    return getResourceReferenceType(element) != ResourceReferenceType.NONE;
  }

  /**
   * Returns the type of resource reference for the given PSiElement; for R fields and android.R
   * fields it will return {@link ResourceReferenceType#APP} and {@link ResourceReferenceType#FRAMEWORK}
   * respectively, and otherwise it returns {@link ResourceReferenceType#NONE}.
   * <p>
   * The element can either be an identifier such as y in R.x.y, or the expression R.x.y itself.
   */
  @NotNull
  public static ResourceReferenceType getResourceReferenceType(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element);
    }

    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      return getResourceReferenceType((PsiReferenceExpression)element.getParent());
    }

    return ResourceReferenceType.NONE;
  }

  /**
   * Returns the resource name; e.g. for "R.string.foo" it returns "foo".
   * NOTE: This method should only be called for elements <b>known</b> to be
   * resource references!
   * */
  @NotNull
  public static String getResourceName(@NotNull PsiElement element) {
    assert isResourceReference(element);
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExp = (PsiReferenceExpression)element;
      String name = refExp.getReferenceName();
      if (name != null) {
        return name;
      }
    }

    return element.getText();
  }

  @NotNull
  public static ResourceReferenceType getResourceReferenceType(PsiReferenceExpression element) {
    PsiExpression exp = element.getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return ResourceReferenceType.NONE;
    }

    exp = ((PsiReferenceExpression)exp).getQualifierExpression();
    if (!(exp instanceof PsiReferenceExpression)) {
      return ResourceReferenceType.NONE;
    }

    PsiReferenceExpression ref = (PsiReferenceExpression)exp;
    if (R_CLASS.equals(ref.getReferenceName())) {
      PsiExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression &&
          ANDROID_PKG.equals(((PsiReferenceExpression)qualifierExpression).getReferenceName())) {
        return ResourceReferenceType.FRAMEWORK;
      } else {
        // TODO: Check resolved type to make sure it's not a class with android.R imported?
        // Not super important since we actively discourage importing android.R directly
        // via lint checks and the AndroidImportFilter preventing it from happening automatically
        return ResourceReferenceType.APP;
      }
    }

    return ResourceReferenceType.NONE;
  }

  /** Returns the Android {@link ResourceType} given a PSI reference to an Android resource. */
  @Nullable
  public static ResourceType getResourceType(PsiElement resourceRefElement) {
    if (!isResourceReference(resourceRefElement)) {
      return null;
    }

    PsiReferenceExpression exp = resourceRefElement instanceof PsiReferenceExpression ?
                                 (PsiReferenceExpression)resourceRefElement :
                                 (PsiReferenceExpression)resourceRefElement.getParent();

    PsiExpression qualifierExpression = exp.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return ResourceType.getEnum(qualifierExpression.getLastChild().getText());
  }
}
