/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions;

import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/** Describes different types of zoom actions */
public enum ZoomType {
  /**
   * Zoom to fit (the screen view port)
   */
  // TODO(b/139432440): Only use icons dedicated for zoom controls.
  FIT("Zoom to Fit Screen", StudioIcons.Common.RESET_ZOOM, StudioIcons.LayoutEditor.Toolbar.EXPAND_TO_FIT),

  /**
   * Zoom to fit, but do not zoom more than 100%
   */
  FIT_INTO("Zoom out to Fit Screen", StudioIcons.Common.RESET_ZOOM, null),

  /**
   * Zoom to actual size (100%)
   */
  ACTUAL("100%", null, StudioIcons.Common.ZOOM_ACTUAL),

  /**
   * Zoom in
   */
  // TODO(b/139432440): Only use icons dedicated for zoom controls.
  IN("Zoom In", StudioIcons.Common.ZOOM_IN, StudioIcons.Common.ADD),

  /**
   * Zoom out
   */
  // TODO(b/139432440): Only use icons dedicated for zoom controls.
  OUT("Zoom Out", StudioIcons.Common.ZOOM_OUT, StudioIcons.Common.REMOVE),

  /**
   * Zoom to match the exact device size (depends on the monitor dpi)
   */
  SCREEN("Exact Device Size", null, null);

  ZoomType(@NotNull String label, @Nullable Icon normalIcon, @Nullable Icon floatingIcon) {
    myLabel = label;
    myIcon = normalIcon;
    myFloatingIcon = floatingIcon;
  }

  /** Describes the zoom action to the user */
  public String getLabel() {
    return myLabel;
  }

  /** Returns the icon for this zoom type. Null if this icon is not set. */
  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  /** Returns the icon for this zoom type when used on the DesignSurface. Null for actions not used on the DesignSurface. */
  @Nullable
  public Icon getFloatingIcon() {
    return myFloatingIcon;
  }

  /** Returns true if this zoom type should be shown to the user */
  public boolean showInMenu() {
    return this != FIT_INTO && this != SCREEN; // these are not yet supported
  }

  @Override
  public String toString() {
    return getLabel();
  }

  private final String myLabel;
  private final Icon myIcon;
  private final Icon myFloatingIcon;

  // Zoom percentages
  // 25%, 33%, 50%, 67%, 75%, 90%, 100%, 110%, 125%, 150%, 200%, 300%, 400%, .... +100%
  private static final int[] ZOOM_POINTS = new int[] {
    25, 33, 50, 67, 75, 90, 100, 110, 125, 150, 200
  };

  public static int zoomIn(int percentage) {
    int i = Arrays.binarySearch(ZOOM_POINTS, percentage);
    if (i < 0) {
      // inexact match: jump to nearest
      i = -i - 1;
      if (i == 0) {
        // If we're far down (like 0.1) don't just jump up to 25%, jump 20% on the current zoom point
        if (percentage < (ZOOM_POINTS[0] - ZOOM_POINTS[0]*0.25)) {
          return Math.min((int)(Math.ceil(percentage * 1.2)), ZOOM_POINTS[0]);
        }
      }
      if (i < ZOOM_POINTS.length) {
        return ZOOM_POINTS[i];
      }
      else {
        // Round up to next nearest hundred
        return ((percentage / 100) + 1) * 100;
      }
    }
    else {
      // exact match
      if (i < ZOOM_POINTS.length - 1) {
        return ZOOM_POINTS[i + 1];
      }
      // Just increase by 100 after this: 200, 300, 400, ...
      return percentage + 100;
    }
  }

  public static int zoomOut(int percentage) {
    int i = Arrays.binarySearch(ZOOM_POINTS, percentage);
    if (i < 0) {
      // inexact match: jump to nearest
      i = -i - 1;
      if (i == 0) {
        // If we're far down (like 0.1) don't just jump up to 25%, jump 10%
        return (int)Math.floor(percentage / 1.1);
      }
      if (i < ZOOM_POINTS.length) {
        return ZOOM_POINTS[i - 1];
      }
      else {
        // Round down to next nearest hundred
        return ((percentage / 100) - 1) * 100;
      }
    }
    else {
      // exact match
      if (i > 0) {
        return ZOOM_POINTS[i - 1];
      }
      return (int)Math.floor(percentage / 1.1);
    }
  }
}