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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.pom.java.LanguageLevel;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class NewProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verify able to create a new project with name containing a space.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14579001
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project with min sdk 23.
   *   2. Enter a project name with at least one space.
   *   3. Accept all other defaults.
   *   4. Wait for build to finish.
   *   5. Project is created successfully.
   *   Verify:
   *   Successfully created new project with name containing a space.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void createNewProjectNameWithSpace() {
    EditorFixture editor = newProject("Test Application").withMinSdk("23").create()
      .getEditor()
      .open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);
    String text = editor.getCurrentFileContents();
    assertThat(text).contains("Test Application");
  }

  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14578805
   * <p>
   *   <pre>
   *   Steps:
   *   1. From the welcome screen, click on "Start a new Android Studio project".
   *   2. Enter a unique project name.
   *   3. Accept all other defaults.
   *   Verify:
   *   1. Check that the project contains 2 module.
   *   2. Check that MainActivity is in AndroidManifest.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewMobileProject() {
    IdeFrameFixture ideFrame = newProject("Test Application").create();
    assertThat(ideFrame.getModuleNames()).containsExactly("app", "TestApplication");

    // Make sure that the activity registration uses the relative syntax
    // (regression test for https://code.google.com/p/android/issues/detail?id=76716)
    String androidManifestContents = ideFrame.getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    assertThat(androidManifestContents).contains("\".MainActivity\"");
  }

  /**
   * Verify module properties can be modified.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: 14606134
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project.
   *   2. Create a new library module and an application module.
   *   3. Right click on the library module and select Change Module Settings.
   *   4. Make a few changes to the properties, like build tools version.
   *   5. Repeat with application module
   *   Verify:
   *   1. Module setting can be updated and project builds successfully.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37742127
  @Test
  public void changeLibraryModuleSettings() throws  Exception {
    String gradleFileContents = newProject("MyTestApp").withMinSdk("24").create()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("library-module")
      .clickFinish()
      .getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, "MyTestApp", "library-module")
      .openFromMenu(ProjectStructureDialogFixture::find, "Open Module Settings")
      .selectPropertiesTab()
      .setCompileSdkVersion("API 24: Android 7.0 (Nougat)")
      .setBuildToolsVersion("25.0.1")
      .setIgnoreAssetsPattern("TestIgnoreAssetsPattern")
      .setIncrementalDex(false)
      .setSourceCompatibility("1.7")
      .setTargetCompatibility("1.7")
      .clickOk()
      .getEditor()
      .open("/library-module/build.gradle")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains("compileSdkVersion 24");
    assertThat(gradleFileContents).contains("buildToolsVersion '25.0.1'");
    assertThat(gradleFileContents).contains("aaptOptions {\n        ignoreAssetsPattern 'TestIgnoreAssetsPattern'\n    }");
    assertThat(gradleFileContents).contains("dexOptions {\n        incremental false\n    }");
    assertThat(gradleFileContents).contains(
      "compileOptions {\n        sourceCompatibility JavaVersion.VERSION_1_7\n        targetCompatibility JavaVersion.VERSION_1_7\n    }");
  }

  @Test
  public void testNoWarningsInNewProjects() throws IOException {
    // Creates a new default project, and checks that if we run Analyze > Inspect Code, there are no warnings.
    // This checks that our (default) project templates are warnings-clean.
    // The test then proceeds to make a couple of edits and checks that these do not generate additional
    // warnings either.
    newProject("Test Application").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    String inspectionResults = guiTest.ideFrame()
      .getEditor()
      .open("app/build.gradle", EditorFixture.Tab.EDITOR)
      .moveBetween("", "applicationId")
      .enterText("resValue \"string\", \"foo\", \"Typpo Here\"\n")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    assertThat(inspectionResults).isEqualTo(lines(
      "Project '" + guiTest.getProjectPath() + "' TestApplication",
      // This warning is from the "foo" string we created in the Gradle resValue declaration above
      "    Android Lint: Performance",
      "        Unused resources",
      "            build.gradle",
      "                The resource 'R.string.foo' appears to be unused",

      // This warning is unfortunate. We may want to get rid of it.
      "    Android Lint: Security",
      "        AllowBackup/FullBackupContent Problems",
      "            AndroidManifest.xml",
      "                On SDK version 23 and up, your app data will be automatically backed up and restored on app install. Consider adding the attribute 'android:fullBackupContent' to specify an '@xml' resource which configures which files to backup. More info: <a href=\"https://developer.android.com/training/backup/autosyncapi.html\">https://developer.android.com/training/backup/autosyncapi.html</a>",

      // This warning is wrong: http://b.android.com/192605
      "    Android Lint: Usability",
      "        Missing support for Firebase App Indexing",
      "            AndroidManifest.xml",
      "                App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent filter. See issue explanation for more details."));
  }

  @Test
  public void testInferNullity() throws IOException {
    // Creates a new default project, adds a nullable API and then invokes Infer Nullity and
    // confirms that it adds nullability annotations.
    newProject("Test Infer Nullity Application").withPackageName("my.pkg").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    IdeFrameFixture frame = guiTest.ideFrame();
    EditorFixture editor = frame.getEditor();

    editor
      .open("app/src/main/java/my/pkg/MainActivity.java", EditorFixture.Tab.EDITOR)
      .moveBetween(" ", "}")
      .enterText("if (savedInstanceState != null) ;");

    frame
      .openFromMenu(InferNullityDialogFixture::find, "Analyze", "Infer Nullity...")
      .clickOk();

    // Text will be updated when analysis is done
    Wait.seconds(30).expecting("matching nullness")
      .until(() -> {
        String file = editor.getCurrentFileContents();
        return file.contains("@Nullable Bundle savedInstanceState");
      });
  }

  private static String lines(String... strings) {
    StringBuilder sb = new StringBuilder();
    for (String s : strings) {
      sb.append(s).append('\n');
    }
    return sb.toString();
  }

  @Test
  public void testRenderResourceInitialization() throws IOException {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76966
    newProject("Test Application").withBriefNames().withMinSdk("9").create();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    assertThat(editor.getCurrentFileName()).isEqualTo("A.java");
    editor.close();
    assertThat(editor.getCurrentFileName()).isEqualTo("activity_a.xml");

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish(Wait.seconds(10));
    guiTest.ideFrame().invokeProjectMake();
    layoutEditor.waitForRenderToFinish();
    assertFalse(layoutEditor.hasRenderErrors());
    guiTest.waitForBackgroundTasks();
  }

  @Test
  public void testLanguageLevelForApi21() {
    newProject("Test Application").withBriefNames().withMinSdk("21").create();

    AndroidModuleModel appAndroidModel = guiTest.ideFrame().getAndroidProjectForModule("app");

    assertThat(appAndroidModel.getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion().getApiString())
      .named("minSdkVersion API").isEqualTo("21");
    assertThat(appAndroidModel.getJavaLanguageLevel()).named("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_7);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(guiTest.ideFrame().getProject());
    assertThat(projectExt.getLanguageLevel()).named("Project Java language level").isSameAs(LanguageLevel.JDK_1_7);
    for (Module module : ModuleManager.getInstance(guiTest.ideFrame().getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtensionImpl.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).named("Gradle Java language level in module " + module.getName())
        .isSameAs(LanguageLevel.JDK_1_7);
    }
  }

  @Test
  public void testGradleWrapperIsExecutable() throws Exception {
    Assume.assumeTrue("Is Unix", SystemInfo.isUnix);
    newProject("Test Application").withBriefNames().create();

    File gradleFile = new File(guiTest.getProjectPath(), SdkConstants.FN_GRADLE_WRAPPER_UNIX);
    assertTrue(gradleFile.canExecute());
  }

  @Test
  public void testStillBuildingMessage() throws Exception {
    // Create a new project and open a layout file.
    // If the first build is still going on when the rendering happens, simply show a message that a build is going on,
    // and check that the message disappears at the end of the build.
    newProject("Test Application").withBriefNames().withMinSdk("15").withoutSync().create();
    final EditorFixture editor = guiTest.ideFrame().getEditor();

    Wait.seconds(5).expecting("file to open").until(() -> "A.java".equals(editor.getCurrentFileName()));

    editor.open("app/src/main/res/layout/activity_a.xml", EditorFixture.Tab.EDITOR);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish(Wait.seconds(10));

    if (layoutEditor.hasRenderErrors()) {
      layoutEditor.waitForErrorPanelToContain("still building");
      assertFalse(layoutEditor.getIssuePanel().containsText("Missing styles"));
      guiTest.ideFrame().waitForGradleProjectSyncToFinish();
      layoutEditor.waitForRenderToFinish();
      assertThat(layoutEditor.hasRenderErrors()).isFalse();
    }
  }

  @Test // http://b.android.com/227918
  public void scrollingActivityFollowedByBasicActivity() throws Exception {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    File projectPath = newProjectWizard
      .getConfigureAndroidProjectStep()
      .enterApplicationName("My Test App")
      .enterPackageName("com.test.project")
      .getLocationInFileSystem();

    guiTest.setProjectPath(projectPath);
    newProjectWizard
      .clickNext()
      .clickNext() // Default Form Factor
      .chooseActivity("Scrolling Activity")
      .clickNext()
      .clickPrevious()
      .chooseActivity("Basic Activity")
      .clickNext()
      .clickFinish();

    guiTest.ideFrame().getEditor()
      .open("app/src/main/res/layout/content_main.xml")
      .open("app/src/main/res/layout/activity_main.xml")
      .open("app/src/main/java/com/test/project/MainActivity.java");
  }

  @NotNull
  private NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }

  /**
   * Describes a new test project to be created.
   */
  private class NewProjectDescriptor {
    private String myActivity = "MainActivity";
    private String myPkg = "com.android.test.app";
    private String myMinSdk = "15";
    private String myName = "TestProject";
    private String myDomain = "com.android";
    private boolean myWaitForSync = true;

    private NewProjectDescriptor(@NotNull String name) {
      withName(name);
    }

    /**
     * Set a custom package to use in the new project
     */
    NewProjectDescriptor withPackageName(@NotNull String pkg) {
      myPkg = pkg;
      return this;
    }

    /**
     * Set a new project name to use for the new project
     */
    NewProjectDescriptor withName(@NotNull String name) {
      myName = name;
      return this;
    }

    /**
     * Set a custom activity name to use in the new project
     */
    NewProjectDescriptor withActivity(@NotNull String activity) {
      myActivity = activity;
      return this;
    }

    /**
     * Set a custom minimum SDK version to use in the new project
     */
    NewProjectDescriptor withMinSdk(@NotNull String minSdk) {
      myMinSdk = minSdk;
      return this;
    }

    /**
     * Set a custom company domain to enter in the new project wizard
     */
    NewProjectDescriptor withCompanyDomain(@NotNull String domain) {
      myDomain = domain;
      return this;
    }

    /**
     * Picks brief names in order to make the test execute faster (less slow typing in name text fields)
     */
    NewProjectDescriptor withBriefNames() {
      withActivity("A").withCompanyDomain("C").withName("P").withPackageName("a.b");
      return this;
    }

    /** Turns off the automatic wait-for-sync that normally happens on {@link #create} */
    NewProjectDescriptor withoutSync() {
      myWaitForSync = false;
      return this;
    }

    /**
     * Creates a project fixture for this description
     */
    IdeFrameFixture create() {
      NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
        .createNewProject();

      ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
      configureAndroidProjectStep.enterApplicationName(myName).enterCompanyDomain(myDomain).enterPackageName(myPkg);
      guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());
      newProjectWizard.clickNext();

      newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, myMinSdk);
      newProjectWizard.clickNext();

      // Skip "Add Activity" step
      newProjectWizard.clickNext();

      newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);

      newProjectWizard.clickFinish();

      if (myWaitForSync) {
        guiTest.ideFrame().waitForGradleProjectSyncToFinish();
      }
      return guiTest.ideFrame();
    }
  }
}
