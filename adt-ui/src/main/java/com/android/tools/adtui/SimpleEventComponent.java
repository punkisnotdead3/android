/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui;

import com.android.tools.adtui.model.*;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A chart component that renders a series of events as icons.
 */
public class SimpleEventComponent<E extends Enum<E>> extends AnimatedComponent {

  public static final Color LINE_COLOR = new Color(166, 129, 184);
  private static final int LINE_OFFSET = 3;
  private static final int NORMALIZED_END = 1;

  /**
   * Enum that defines the action of an event, used when the event has a Down, and Up action such
   * as MouseClick
   */
  public enum Action {
    NONE,
    DOWN,
    UP
  }

  private static final long HOLD_DELAY_US = TimeUnit.MILLISECONDS.toMicros(150);

  @NotNull
  private final RangedSeries<EventAction<Action, E>> mData;

  @NotNull
  private final Icon[] mIcons;

  @NotNull
  private final ArrayList<Shape> mPaths;

  @NotNull
  private final ArrayList<EventRenderData> mIconsToDraw;

  @NotNull
  private final int mImageWidth;

  /**
   * Component that renders EventActions as a series of icons.
   */
  public SimpleEventComponent(
    @NotNull RangedSeries<EventAction<Action, E>> data,
    @NotNull Icon[] icons) {
    mData = data;
    mIcons = icons;
    mPaths = new ArrayList<Shape>();
    mIconsToDraw = new ArrayList<EventRenderData>();
    mImageWidth = mIcons[0].getIconWidth();
  }

  @Override
  protected void updateData() {
    //TODO Pull logic of combining events out of component and into EventHandler
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();
    EventAction<Action, ? extends Enum> downEvent = null;
    mIconsToDraw.clear();
    mPaths.clear();
    ImmutableList<SeriesData<EventAction<Action, E>>> series = mData.getSeries();
    int size = series.size();

    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<Action, E>> seriesData = series.get(i);
      EventAction<Action, ? extends Enum> data = seriesData.value;

      if (data.getValue() == Action.DOWN) {
        downEvent = data;
      } else if (data.getValue() == Action.UP) {
        downEvent = null;
        Integer toDraw = data.getValueData().ordinal();
        if (data.getEndUs() - data.getStartUs() >= HOLD_DELAY_US) {
          toDraw = data.getValueData().ordinal();
          Path2D.Float path = new Path2D.Float();
          double start = (data.getStartUs() - min) / (max - min);
          double end = (data.getEndUs() - min) / (max - min);
          path.moveTo(start, LINE_OFFSET);
          path.lineTo(end, LINE_OFFSET);
          mPaths.add(path);
        }
        mIconsToDraw.add(new EventRenderData(toDraw, data.getEndUs()));
      }
    }
    if (downEvent != null) {
      double start = (downEvent.getStartUs() - min) / (max - min);
      Integer toDraw = downEvent.getValueData().ordinal();
      if (max - downEvent.getStartUs() >= HOLD_DELAY_US) {
        Path2D.Float path = new Path2D.Float();
        double end = 1;
        path.moveTo(start, LINE_OFFSET);
        path.lineTo(end, LINE_OFFSET);
        mPaths.add(path);
      }
      mIconsToDraw.add(new EventRenderData(toDraw, 0));
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();
    double scaleFactor = dim.getWidth();
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, 1);
    for (int i = 0; i < mIconsToDraw.size(); i++) {
      EventRenderData data = mIconsToDraw.get(i);
      double normalizedPosition = ((data.getTimestamp() - min) / (max - min));
      if (data.getTimestamp() == 0) {
        normalizedPosition = NORMALIZED_END;
      }
      //TODO account for image width in positioning.
      AffineTransform translate = AffineTransform
        .getTranslateInstance(normalizedPosition * scaleFactor - mImageWidth / 2.0, 0);
      mIcons[data.getIndex() % mIcons.length].paintIcon(this, g2d, (int)translate.getTranslateX(), (int)translate.getTranslateY());
    }
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(2.0f);
    g2d.setStroke(str);
    g2d.setColor(LINE_COLOR);
    for (int i = 0; i < mPaths.size(); i++) {
      Shape shape = scale.createTransformedShape(mPaths.get(i));
      g2d.draw(shape);
    }
    g2d.setStroke(current);
  }

  @Override
  protected void debugDraw(Graphics2D g) {
    super.debugDraw(g);
  }
}

