/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.GradleValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the extra user-defined properties defined in the Gradle file.
 * <p>
 * For more details please read
 * <a href="https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties">Extra Properties</a>.
 * </p>
 */
public final class ExtModel extends GradleDslBlockModel {

  public ExtModel(@NotNull ExtDslElement dslElement) {
    super(dslElement);
  }

  public <T> T getProperty(@NotNull String property, @NotNull Class<T> clazz) {
    return myDslElement.getProperty(property, clazz);
  }

  /**
   * Returns the property value along with variable resolution history.
   *
   * Note: WIP. Please do not use.
   */
  public <T> GradleValue<T> getPropertyWithResolutionHistory(@NotNull String property, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = myDslElement.getPropertyElement(property);
    if (propertyElement != null) {
      T resultValue = null;
      if (clazz.isInstance(propertyElement)) {
        resultValue = clazz.cast(propertyElement);
      }
      else if (propertyElement instanceof GradleDslExpression) {
        resultValue = ((GradleDslExpression)propertyElement).getValue(clazz);
      }
      if (resultValue != null) {
        return new GradleValue<T>(resultValue, propertyElement);
      }
    }
    return null;
  }
}
