/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI.CurrentTheme.Table;
import icons.StudioIcons;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JLabel;
import javax.swing.LayoutStyle.ComponentPlacement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DetailsPanel extends JBPanel<DetailsPanel> {
  private final @NotNull Component myHeadingLabel;
  private final @NotNull AbstractButton myCloseButton;
  protected final @NotNull Collection<@NotNull InfoSection> myInfoSections;
  private final @NotNull Container myInfoSectionPanel;
  private final @NotNull Component myScrollPane;

  protected DetailsPanel(@NotNull String heading) {
    super(null);

    myHeadingLabel = newHeadingLabel(heading);
    myCloseButton = Buttons.newIconButton(StudioIcons.Common.CLOSE);
    myInfoSections = new ArrayList<>();

    myInfoSectionPanel = new JBPanel<>(null);
    myInfoSectionPanel.setBackground(Table.BACKGROUND);

    myScrollPane = new JBScrollPane(myInfoSectionPanel);
  }

  static @NotNull Component newHeadingLabel(@NotNull String heading) {
    Component label = new JBLabel(heading);
    label.setFont(label.getFont().deriveFont(Font.BOLD));

    return label;
  }

  protected static void setText(@NotNull JLabel label, @Nullable Object value) {
    if (value == null) {
      return;
    }

    label.setText(value.toString());
  }

  protected static void setText(@NotNull JLabel label, @NotNull Iterable<@NotNull String> values) {
    label.setText(String.join(", ", values));
  }

  protected final void init() {
    setNameLabelPreferredWidthsToMax();

    setInfoSectionPanelLayout();
    setLayout();
  }

  private void setNameLabelPreferredWidthsToMax() {
    Collection<Component> labels = myInfoSections.stream()
      .map(InfoSection::getNameLabels)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    OptionalInt optionalWidth = labels.stream()
      .map(Component::getPreferredSize)
      .mapToInt(size -> size.width)
      .max();

    int width = optionalWidth.orElseThrow(AssertionError::new);

    labels.forEach(label -> {
      Dimension size = label.getPreferredSize();
      size.width = width;

      label.setPreferredSize(size);
      label.setMaximumSize(size);
    });
  }

  private void setInfoSectionPanelLayout() {
    GroupLayout layout = new GroupLayout(myInfoSectionPanel);

    Group horizontalGroup = layout.createParallelGroup();
    SequentialGroup verticalGroup = layout.createSequentialGroup();

    Iterator<InfoSection> i = myInfoSections.iterator();
    Component section = i.next();

    horizontalGroup.addComponent(section);
    verticalGroup.addComponent(section);

    while (i.hasNext()) {
      section = i.next();
      horizontalGroup.addComponent(section);

      verticalGroup
        .addPreferredGap(ComponentPlacement.UNRELATED)
        .addComponent(section);
    }

    layout.setAutoCreateContainerGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myInfoSectionPanel.setLayout(layout);
  }

  private void setLayout() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(myHeadingLabel)
                  .addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(myCloseButton)
                  .addContainerGap())
      .addComponent(myScrollPane);

    Group verticalGroup = layout.createSequentialGroup()
      .addContainerGap()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myHeadingLabel)
                  .addComponent(myCloseButton))
      .addPreferredGap(ComponentPlacement.RELATED)
      .addComponent(myScrollPane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, 242);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  public final @NotNull AbstractButton getCloseButton() {
    return myCloseButton;
  }
}
