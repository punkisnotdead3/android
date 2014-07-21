/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.timing.Pause;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class TestLaunchingIdeFromTest extends GuiTestCase {
  @Test
  public void testCreateNewProject() {
    FrameFixture welcomeFrame = WindowFinder.findFrame(WelcomeFrame.class).using(myRobot);
    ActionButtonWithText createNewProjectButton =
      myRobot.finder().find(welcomeFrame.target, new GenericTypeMatcher<ActionButtonWithText>(ActionButtonWithText.class) {
        @Override
        protected boolean isMatching(ActionButtonWithText buttonWithText) {
          AnAction action = buttonWithText.getAction();
          if (action != null) {
            String id = ActionManager.getInstance().getId(action);
            return "WelcomeScreen.CreateNewProject".equals(id);
          }
          return false;
        }
      });
    myRobot.click(createNewProjectButton);
    Pause.pause(1, TimeUnit.MINUTES);
  }
}
