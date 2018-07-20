/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import org.jetbrains.annotations.Nullable;

public class SketchStyle {
  private final SketchBorderOptions borderOptions;
  private final SketchBorder[] borders;
  private final SketchFill[] fills;
  private final short miterLimit;
  private final short windingRule;

  public SketchStyle(@Nullable SketchBorderOptions borderOptions,
                     @Nullable SketchBorder[] borders,
                     @Nullable SketchFill[] fills,
                     short miterLimit,
                     short windingRule) {
    this.borderOptions = borderOptions;
    this.borders = borders;
    this.fills = fills;
    this.miterLimit = miterLimit;
    this.windingRule = windingRule;
  }

  public SketchBorderOptions getBorderOptions() {
    return borderOptions;
  }

  public SketchBorder[] getBorders() {
    return borders;
  }

  public SketchFill[] getFills() {
    return fills;
  }

  public short getMiterLimit() {
    return miterLimit;
  }

  public short getWindingRule() {
    return windingRule;
  }
}
