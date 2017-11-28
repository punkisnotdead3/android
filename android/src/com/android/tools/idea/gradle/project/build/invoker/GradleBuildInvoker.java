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
package com.android.tools.idea.gradle.project.build.invoker;

<<<<<<< HEAD
import com.android.tools.idea.gradle.project.BuildSettings;
=======
import com.android.SdkConstants;
import com.android.builder.model.BaseArtifact;
import com.android.tools.idea.gradle.filters.AndroidReRunBuildFilter;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
>>>>>>> goog/upstream-ij17
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
<<<<<<< HEAD
=======
import com.google.common.collect.*;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.*;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.build.output.JavacOutputParser;
import com.intellij.build.output.KotlincOutputParser;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
>>>>>>> goog/upstream-ij17
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
<<<<<<< HEAD
import org.gradle.tooling.BuildAction;
=======
import one.util.streamex.StreamEx;
import org.jetbrains.android.facet.AndroidFacet;
>>>>>>> goog/upstream-ij17
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.BuildMode.*;
<<<<<<< HEAD
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
=======
import static com.android.tools.idea.gradle.util.GradleBuilds.*;
>>>>>>> goog/upstream-ij17
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
<<<<<<< HEAD
=======
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.jetbrains.android.util.AndroidCommonUtils.isInstrumentationTestConfiguration;
import static org.jetbrains.android.util.AndroidCommonUtils.isTestConfiguration;
>>>>>>> goog/upstream-ij17

/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleBuildInvoker {
  @NotNull private final Project myProject;
  @NotNull private final FileDocumentManager myDocumentManager;
  @NotNull private final GradleTasksExecutorFactory myTaskExecutorFactory;

  @NotNull private final Set<AfterGradleInvocationTask> myAfterTasks = new LinkedHashSet<>();
  @NotNull private final List<String> myOneTimeGradleOptions = new ArrayList<>();
  @NotNull private final Multimap<String, String> myLastBuildTasks = ArrayListMultimap.create();
  @NotNull private final BuildStopper myBuildStopper = new BuildStopper();

  @NotNull
  public static GradleBuildInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleBuildInvoker.class);
  }

  public GradleBuildInvoker(@NotNull Project project, @NotNull FileDocumentManager documentManager) {
    this(project, documentManager, new GradleTasksExecutorFactory());
  }

  @VisibleForTesting
  protected GradleBuildInvoker(@NotNull Project project,
                               @NotNull FileDocumentManager documentManager,
                               @NotNull GradleTasksExecutorFactory tasksExecutorFactory) {
    myProject = project;
    myDocumentManager = documentManager;
    myTaskExecutorFactory = tasksExecutorFactory;
  }

  public void cleanProject() {
    setProjectBuildMode(CLEAN);

    // "Clean" also generates sources.
<<<<<<< HEAD
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    List<String> tasks = new ArrayList<>(GradleTaskFinder.getInstance().findTasksToExecute(modules, SOURCE_GEN, TestCompileType.ALL));
    addCleanTask(tasks);
    executeTasks(tasks, Collections.singletonList(createGenerateSourcesOnlyProperty()));
  }

  public void cleanAndGenerateSources() {
    generateSources(true /* clean project */);
=======
    ListMultimap<Path, String> tasks = findTasksToExecute(moduleManager.getModules(), SOURCE_GEN, TestCompileType.NONE);
    tasks.keys().elementSet().forEach(key -> tasks.get(key).add(0, CLEAN_TASK_NAME));
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.singletonList(createGenerateSourcesOnlyProperty()));
    }
  }

  public void assembleTranslate() {
    setProjectBuildMode(ASSEMBLE_TRANSLATE);

    Set<String> rootPaths = Sets.newHashSet();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      String gradlePath = getGradleProjectPath(module);
      if (isEmpty(gradlePath)) continue;
      rootPaths.add(ExternalSystemApiUtil.getExternalRootProjectPath(module));
    }

    for (String rootPath : rootPaths) {
      executeTasks(new File(rootPath), Collections.singletonList(ASSEMBLE_TRANSLATE_TASK_NAME));
    }
>>>>>>> goog/upstream-ij17
  }

  public void generateSources() {
    generateSources(false /* do not clean project */);
  }

  private void generateSources(boolean cleanProject) {
    BuildMode buildMode = SOURCE_GEN;
    setProjectBuildMode(buildMode);

<<<<<<< HEAD
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    List<String> tasks = new ArrayList<>(GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, TestCompileType.ALL));
    if (cleanProject) {
      addCleanTask(tasks);
=======
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    ListMultimap<Path, String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    if (cleanProject) {
      tasks.keys().elementSet().forEach(key -> tasks.get(key).add(0, CLEAN_TASK_NAME));
    }
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.singletonList(createGenerateSourcesOnlyProperty()));
>>>>>>> goog/upstream-ij17
    }
  }

  private static void addCleanTask(@NotNull List<String> tasks) {
    tasks.add(0, CLEAN_TASK_NAME);
  }

  @NotNull
  private static String createGenerateSourcesOnlyProperty() {
    return createProjectProperty(PROPERTY_GENERATE_SOURCES_ONLY, true);
  }

  /**
   * Execute Gradle tasks that compile the relevant Java sources.
   *
   * @param modules         Modules that need to be compiled
   * @param testCompileType Kind of tests that the caller is interested in. Use {@link TestCompileType#ALL} if compiling just the
   *                        main sources, {@link TestCompileType#UNIT_TESTS} if class files for running unit tests are needed.
   */
  public void compileJava(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = COMPILE_JAVA;
    setProjectBuildMode(buildMode);
<<<<<<< HEAD
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks);
=======
    ListMultimap<Path, String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath));
    }
>>>>>>> goog/upstream-ij17
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    assemble(modules, testCompileType, Collections.emptyList(), null);
  }

  public void assemble(@NotNull Module[] modules,
                       @NotNull TestCompileType testCompileType,
                       @NotNull List<String> arguments,
                       @Nullable BuildAction<?> buildAction) {
    BuildMode buildMode = ASSEMBLE;
    setProjectBuildMode(buildMode);
<<<<<<< HEAD
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks, arguments, buildAction);
=======
    ListMultimap<Path, String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), arguments);
    }
>>>>>>> goog/upstream-ij17
  }

  public void rebuild() {
    BuildMode buildMode = REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
<<<<<<< HEAD
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.ALL);
    executeTasks(tasks);
=======
    ListMultimap<Path, String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath));
    }
>>>>>>> goog/upstream-ij17
  }

  /**
   * Execute the last run set of Gradle tasks, with the specified gradle options prepended before the tasks to run.
   */
  public void rebuildWithTempOptions(@NotNull File buildFilePath, @NotNull List<String> options) {
    myOneTimeGradleOptions.addAll(options);
    try {
      Collection<String> tasks = myLastBuildTasks.get(buildFilePath.getPath());
      if (tasks.isEmpty()) {
        // For some reason the IDE lost the Gradle tasks executed during the last build.
        rebuild();
      }
      else {
        // The use case for this is the following:
        // 1. the build fails, and the console has the message "Run with --stacktrace", which now is a hyperlink
        // 2. the user clicks the hyperlink
        // 3. the IDE re-runs the build, with the Gradle tasks that were executed when the build failed, and it adds "--stacktrace"
        //    to the command line arguments.
        List<String> tasksFromLastBuild = new ArrayList<>();
        tasksFromLastBuild.addAll(tasks);
        executeTasks(buildFilePath, tasksFromLastBuild);
      }
    }
    finally {
      // Don't reuse them on the next rebuild.
      myOneTimeGradleOptions.clear();
    }
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
  }

<<<<<<< HEAD
=======
  @NotNull
  public static ListMultimap<Path, String> findCleanTasksForModules(@NotNull Module[] modules) {
    ListMultimap<Path, String> tasks = ArrayListMultimap.create();
    for (Module module : modules) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
      String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (isEmpty(rootProjectPath)) {
        continue;
      }
      addTaskIfSpecified(tasks.get(Paths.get(rootProjectPath)), gradlePath, CLEAN_TASK_NAME);
    }
    return tasks;
  }

  @NotNull
  public static ListMultimap<Path, String> findTasksToExecute(@NotNull Module[] modules,
                                                              @NotNull BuildMode buildMode,
                                                              @NotNull TestCompileType testCompileType) {
    ListMultimap<Path, String> tasks = ArrayListMultimap.create();

    if (ASSEMBLE == buildMode) {
      Project project = modules[0].getProject();
      if (GradleSyncState.getInstance(project).lastSyncFailed()) {
        // If last Gradle sync failed, just call "assemble" at the top-level. Without a model there are no other tasks we can call.
        StreamEx.of(modules)
          .map(module -> ExternalSystemApiUtil.getExternalRootProjectPath(module))
          .nonNull()
          .distinct()
          .map(path -> Paths.get(path))
          .forEach(path -> tasks.put(path, DEFAULT_ASSEMBLE_TASK_NAME));
        return tasks;
      }
    }

    for (Module module : modules) {
      if (BUILD_SRC_FOLDER_NAME.equals(module.getName())) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }
      String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (isEmpty(rootProjectPath)) {
        continue;
      }

      List<String> moduleTasks = findGradleBuildTasks(module, buildMode, testCompileType);
      tasks.putAll(Paths.get(rootProjectPath), moduleTasks);
    }
    if (buildMode == REBUILD && !tasks.isEmpty()) {
      tasks.keys().elementSet().forEach(key -> tasks.get(key).add(0, CLEAN_TASK_NAME));
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      getLogger().info(String.format(format, modules[0].getProject().getName(), buildMode.name()));
    }
    return tasks;
  }

  /**
   * @deprecated use {@link GradleBuildInvoker#executeTasks(File, List)}
   */
>>>>>>> goog/upstream-ij17
  public void executeTasks(@NotNull List<String> gradleTasks) {
    File path = getBaseDirPath(myProject);
    executeTasks(path, gradleTasks, myOneTimeGradleOptions);
  }

  public void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks) {
    executeTasks(buildFilePath, gradleTasks, myOneTimeGradleOptions);
  }

<<<<<<< HEAD
  public void executeTasks(@NotNull List<String> tasks,
                           @Nullable BuildMode buildMode,
                           @NotNull List<String> commandLineArguments,
                           @Nullable BuildAction buildAction) {
    if (buildMode != null) {
      setProjectBuildMode(buildMode);
    }
    executeTasks(tasks, commandLineArguments, buildAction);
  }

  public void executeTasks(@NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
    executeTasks(gradleTasks, commandLineArguments, null);
  }

  private void executeTasks(@NotNull List<String> gradleTasks,
                            @NotNull List<String> commandLineArguments,
                            @Nullable BuildAction buildAction) {
=======
  public void executeTasks(@NotNull ListMultimap<Path, String> tasks,
                           @Nullable BuildMode buildMode,
                           @NotNull List<String> commandLineArguments) {
    if (buildMode != null) {
      setProjectBuildMode(buildMode);
    }
    tasks.keys().elementSet().forEach(path -> {
      executeTasks(path.toFile(), tasks.get(path), commandLineArguments);
    });
  }

  public void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
>>>>>>> goog/upstream-ij17
    List<String> jvmArguments = new ArrayList<>();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Projects in tests may not have a local.properties, set ANDROID_HOME JVM argument if that's the case.
      LocalProperties localProperties;
      try {
        localProperties = new LocalProperties(myProject);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (localProperties.getAndroidSdkPath() == null) {
        File androidHomePath = IdeSdks.getInstance().getAndroidSdkPath();
        // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
        if (androidHomePath != null) {
          jvmArguments.add(AndroidGradleSettings.createAndroidHomeJvmArg(androidHomePath.getPath()));
        }
      }
    }

    Request request = new Request(myProject, buildFilePath, gradleTasks);
    ExternalSystemTaskNotificationListener buildTaskListener = createBuildTaskListener(request, "Build");
    // @formatter:off
    request.setJvmArguments(jvmArguments)
           .setCommandLineArguments(commandLineArguments)
<<<<<<< HEAD
           .setBuildAction(buildAction);
=======
           .setTaskListener(buildTaskListener);
>>>>>>> goog/upstream-ij17
    // @formatter:on
    executeTasks(request);
  }

  @NotNull
  public ExternalSystemTaskNotificationListener createBuildTaskListener(@NotNull Request request, String executionName) {
    BuildViewManager buildViewManager = ServiceManager.getService(myProject, BuildViewManager.class);
    List<BuildOutputParser> buildOutputParsers = Lists.newArrayList(new JavacOutputParser(), new KotlincOutputParser());
    //noinspection IOResourceOpenedButNotSafelyClosed
    final BuildOutputInstantReaderImpl buildOutputInstantReader =
      new BuildOutputInstantReaderImpl(request.myTaskId, buildViewManager, buildOutputParsers);
    return new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        AnAction restartAction = new AnAction() {
          @Override
          public void update(@NotNull AnActionEvent e) {
            super.update(e);
            Presentation p = e.getPresentation();
            p.setEnabled(!myBuildStopper.contains(id));
          }

          @Override
          public void actionPerformed(AnActionEvent e) {
            executeTasks(request);
          }
        };
        restartAction.getTemplatePresentation().setText("Restart");
        restartAction.getTemplatePresentation().setDescription("Restart");
        restartAction.getTemplatePresentation().setIcon(AllIcons.Actions.Compile);
        long eventTime = System.currentTimeMillis();
        buildViewManager.onEvent(new StartBuildEventImpl(new DefaultBuildDescriptor(id, executionName, workingDir, eventTime), "running...")
                                   .withRestartAction(restartAction)
                                   .withExecutionFilter(new AndroidReRunBuildFilter(workingDir)));
      }

      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        if (event instanceof ExternalSystemTaskExecutionEvent) {
          BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
          buildViewManager.onEvent(buildEvent);
        }
      }

      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        buildViewManager.onEvent(new OutputBuildEventImpl(id, text, stdOut));
        buildOutputInstantReader.append(text);
      }

      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        buildOutputInstantReader.close();
      }

      @Override
      public void onSuccess(@NotNull ExternalSystemTaskId id) {
        buildViewManager.onEvent(new FinishBuildEventImpl(
          id, null, System.currentTimeMillis(), "completed successfully", new SuccessResultImpl()));
      }

      @Override
      public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
        File projectDirPath = getBaseDirPath(myProject);
        String projectName = projectDirPath.getName();
        FailureResultImpl failureResult = ExternalSystemUtil.createFailureResult(e, projectName, GRADLE_SYSTEM_ID, myProject);
        buildViewManager.onEvent(
          new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "build failed", failureResult));
      }
    };
  }

  public void executeTasks(@NotNull Request request) {
    String buildFilePath = request.myBuildFilePath.getPath();
    // Remember the current build's tasks, in case they want to re-run it with transient gradle options.
    myLastBuildTasks.removeAll(buildFilePath);
    List<String> gradleTasks = request.getGradleTasks();
    myLastBuildTasks.putAll(buildFilePath, gradleTasks);

    getLogger().info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    GradleTasksExecutor executor = myTaskExecutorFactory.create(request, myBuildStopper);
    Runnable executeTasksTask = () -> {
      myDocumentManager.saveAllDocuments();
      executor.queue();
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      executeTasksTask.run();
    }
    else if (request.isWaitForCompletion()) {
      executor.queueAndWaitForCompletion();
    }
    else {
<<<<<<< HEAD
      TransactionGuard.getInstance().submitTransactionAndWait(executeTasksTask);
=======
      invokeAndWaitIfNeeded((Runnable)executor::queue);
    }
  }

  /**
   * Saves all edited documents. This method can be called from any thread.
   */
  public static void saveAllFilesSafely() {
    ApplicationManager.getApplication().invokeAndWait(
      () -> ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().saveAllDocuments()));
  }


  @Nullable
  private static String getGradleProjectPath(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return null;
    }

    String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (isEmpty(gradlePath)) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String msg = String.format("Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.",
                                 module.getName());
      getLogger().info(msg);
      return null;
    }
    else {
      return gradlePath;
    }
  }

  @NotNull
  private static List<String> findGradleBuildTasks(@NotNull Module module,
                                                   @NotNull BuildMode buildMode,
                                                   @NotNull TestCompileType testCompileType) {
    List<String> tasks = Lists.newArrayList();
    String gradlePath = getGradleProjectPath(module);
    if (isEmpty(gradlePath)) {
      return tasks;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();

      AndroidModuleModel androidModel = AndroidModuleModel.get(module);

      switch (buildMode) {
        case CLEAN: // Intentional fall-through.
        case SOURCE_GEN:
          addAfterSyncTasks(tasks, gradlePath, properties);
          addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
          break;
        case ASSEMBLE:
          tasks.add(createBuildTask(gradlePath, properties.ASSEMBLE_TASK_NAME));

          // Add assemble tasks for tests.
          if (testCompileType != TestCompileType.NONE) {
            for (BaseArtifact artifact : getArtifactsForTestCompileType(testCompileType, androidModel)) {
              addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
            }
          }
          break;
        default:
          addAfterSyncTasks(tasks, gradlePath, properties);
          addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);

          // When compiling for unit tests, run only COMPILE_JAVA_TEST_TASK_NAME, which will run javac over main and test code. If the
          // Jack compiler is enabled in Gradle, COMPILE_JAVA_TASK_NAME will end up running e.g. compileDebugJavaWithJack, which produces
          // no *.class files and would be just a waste of time.
          if (testCompileType != TestCompileType.UNIT_TESTS) {
            addTaskIfSpecified(tasks, gradlePath, properties.COMPILE_JAVA_TASK_NAME);
          }

          // Add compile tasks for tests.
          for (BaseArtifact artifact : getArtifactsForTestCompileType(testCompileType, androidModel)) {
            addTaskIfSpecified(tasks, gradlePath, artifact.getCompileTaskName());
          }
          break;
      }
    }
    else {
      JavaFacet javaFacet = JavaFacet.getInstance(module);
      if (javaFacet != null && javaFacet.getConfiguration().BUILDABLE) {
        String gradleTaskName = javaFacet.getGradleTaskName(buildMode);
        if (gradleTaskName != null) {
          tasks.add(createBuildTask(gradlePath, gradleTaskName));
        }
        if (testCompileType == TestCompileType.UNIT_TESTS) {
          tasks.add(createBuildTask(gradlePath, JavaFacet.TEST_CLASSES_TASK_NAME));
        }
      }
>>>>>>> goog/upstream-ij17
    }
    return tasks;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleBuildInvoker.class);
  }

<<<<<<< HEAD
  public void stopBuild(@NotNull ExternalSystemTaskId id) {
    myBuildStopper.attemptToStopBuild(id, null);
=======
  private static void addAfterSyncTasksForTestArtifacts(@NotNull List<String> tasks,
                                                        @NotNull String gradlePath,
                                                        @NotNull TestCompileType testCompileType,
                                                        @Nullable AndroidModuleModel androidModel) {
    Collection<BaseArtifact> testArtifacts = getArtifactsForTestCompileType(testCompileType, androidModel);
    for (BaseArtifact artifact : testArtifacts) {
      for (String taskName : getIdeSetupTasks(artifact)) {
        addTaskIfSpecified(tasks, gradlePath, taskName);
      }
    }
  }

  @NotNull
  private static Collection<BaseArtifact> getArtifactsForTestCompileType(@NotNull TestCompileType testCompileType,
                                                                         @Nullable AndroidModuleModel androidModel) {
    if (androidModel == null) {
      return Collections.emptyList();
    }
    BaseArtifact testArtifact = null;
    switch (testCompileType) {
      case NONE:
        // TestCompileType.NONE means clean / compile all / rebuild all, so we need use all test artifacts.
        return androidModel.getTestArtifactsInSelectedVariant();
      case ANDROID_TESTS:
        testArtifact = androidModel.getAndroidTestArtifactInSelectedVariant();
        break;
      case UNIT_TESTS:
        testArtifact = androidModel.getUnitTestArtifactInSelectedVariant();
    }
    return testArtifact != null ? ImmutableList.of(testArtifact) : Collections.emptyList();
  }

  private static void addAfterSyncTasks(@NotNull List<String> tasks,
                                        @NotNull String gradlePath,
                                        @NotNull JpsAndroidModuleProperties properties) {
    // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
    // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
    // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
    // compilation tasks don't depend on it, so we have to call it explicitly.
    for (String taskName : properties.AFTER_SYNC_TASK_NAMES) {
      addTaskIfSpecified(tasks, gradlePath, taskName);
    }
  }

  private static void addTaskIfSpecified(@NotNull List<String> tasks, @NotNull String gradlePath, @Nullable String gradleTaskName) {
    if (isNotEmpty(gradleTaskName)) {
      String buildTask = createBuildTask(gradlePath, gradleTaskName);
      if (!tasks.contains(buildTask)) {
        tasks.add(buildTask);
      }
    }
  }

  @NotNull
  public static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }

  @NotNull
  public static TestCompileType getTestCompileType(@Nullable String runConfigurationId) {
    if (runConfigurationId != null) {
      if (isInstrumentationTestConfiguration(runConfigurationId)) {
        return TestCompileType.ANDROID_TESTS;
      }
      if (isTestConfiguration(runConfigurationId)) {
        return TestCompileType.UNIT_TESTS;
      }
    }
    return TestCompileType.NONE;
  }

  public boolean stopBuild(@NotNull ExternalSystemTaskId id) {
    if (myBuildStopper.contains(id)) {
      myBuildStopper.attemptToStopBuild(id, null);
      return true;
    }
    return false;
>>>>>>> goog/upstream-ij17
  }

  public void add(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.add(task);
  }

  @VisibleForTesting
  @NotNull
  protected AfterGradleInvocationTask[] getAfterInvocationTasks() {
    return myAfterTasks.toArray(new AfterGradleInvocationTask[myAfterTasks.size()]);
  }

  public void remove(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.remove(task);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public interface AfterGradleInvocationTask {
    void execute(@NotNull GradleInvocationResult result);
  }

  public static class Request {
    @NotNull private final Project myProject;
    @NotNull private final File myBuildFilePath;
    @NotNull private final List<String> myGradleTasks;
    @NotNull private final List<String> myJvmArguments;
    @NotNull private final List<String> myCommandLineArguments;
    @NotNull
    private final Map<String, String> myEnv;
    private boolean myPassParentEnvs = true;
    @NotNull private final ExternalSystemTaskId myTaskId;

    @Nullable private ExternalSystemTaskNotificationListener myTaskListener;
<<<<<<< HEAD
    @Nullable private File myBuildFilePath;
    @Nullable private BuildAction myBuildAction;
=======
>>>>>>> goog/upstream-ij17
    private boolean myWaitForCompletion;

<<<<<<< HEAD
    public Request(@NotNull Project project, @NotNull String... gradleTasks) {
      this(project, Arrays.asList(gradleTasks));
=======
    public Request(@NotNull Project project, @NotNull File buildFilePath, @NotNull String... gradleTasks) {
      this(project, buildFilePath, Arrays.asList(gradleTasks));
>>>>>>> goog/upstream-ij17
    }

    public Request(@NotNull Project project, @NotNull File buildFilePath, @NotNull List<String> gradleTasks) {
      this(project, buildFilePath, gradleTasks, ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, EXECUTE_TASK, project));
    }

    public Request(@NotNull Project project,
                   @NotNull File buildFilePath,
                   @NotNull List<String> gradleTasks,
                   @NotNull ExternalSystemTaskId taskId) {
      myProject = project;
      myBuildFilePath = buildFilePath;
      myGradleTasks = new ArrayList<>(gradleTasks);
      myJvmArguments = new ArrayList<>();
      myCommandLineArguments = new ArrayList<>();
      myTaskId = taskId;
      myEnv = new LinkedHashMap<>();
    }

    @NotNull
    Project getProject() {
      return myProject;
    }

    @NotNull
    List<String> getGradleTasks() {
      return myGradleTasks;
    }

    @NotNull
    List<String> getJvmArguments() {
      return myJvmArguments;
    }

    @NotNull
    public Request setJvmArguments(@NotNull List<String> jvmArguments) {
      myJvmArguments.clear();
      myJvmArguments.addAll(jvmArguments);
      return this;
    }

    @NotNull
    List<String> getCommandLineArguments() {
      return myCommandLineArguments;
    }

    @NotNull
    public Request setCommandLineArguments(@NotNull List<String> commandLineArguments) {
      myCommandLineArguments.clear();
      myCommandLineArguments.addAll(commandLineArguments);
      return this;
    }

    public Request withEnvironmentVariables(Map<String, String> envs) {
      myEnv.putAll(envs);
      return this;
    }

    public Map<String, String> getEnv() {
      return Collections.unmodifiableMap(myEnv);
    }

    public Request passParentEnvs(boolean passParentEnvs) {
      myPassParentEnvs = passParentEnvs;
      return this;
    }

    public boolean isPassParentEnvs() {
      return myPassParentEnvs;
    }

    @Nullable
    public ExternalSystemTaskNotificationListener getTaskListener() {
      return myTaskListener;
    }

    @NotNull
    public Request setTaskListener(@Nullable ExternalSystemTaskNotificationListener taskListener) {
      myTaskListener = taskListener;
      return this;
    }

    @NotNull
    ExternalSystemTaskId getTaskId() {
      return myTaskId;
    }

    @NotNull
    File getBuildFilePath() {
      return myBuildFilePath;
    }

    boolean isWaitForCompletion() {
      return myWaitForCompletion;
    }

    @NotNull
    public Request waitForCompletion() {
      myWaitForCompletion = true;
      return this;
    }

    @Nullable
    public BuildAction getBuildAction() {
      return myBuildAction;
    }

    @NotNull
    public Request setBuildAction(@Nullable BuildAction buildAction) {
      myBuildAction = buildAction;
      return this;
    }

    @Override
<<<<<<< HEAD
=======
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request that = (Request)o;
      // We only care about this fields because 'equals' is used for testing only. Production code does not care.
      return Objects.equals(myBuildFilePath, that.myBuildFilePath ) &&
             Objects.equals(myGradleTasks, that.myGradleTasks) &&
             Objects.equals(myJvmArguments, that.myJvmArguments) &&
             Objects.equals(myCommandLineArguments, that.myCommandLineArguments);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBuildFilePath, myGradleTasks, myJvmArguments, myCommandLineArguments);
    }

    @Override
>>>>>>> goog/upstream-ij17
    public String toString() {
      return "RequestSettings{" +
             "myBuildFilePath=" + myBuildFilePath +
             ", myGradleTasks=" + myGradleTasks +
             ", myJvmArguments=" + myJvmArguments +
             ", myCommandLineArguments=" + myCommandLineArguments +
             ", myBuildAction=" + myBuildAction +
             '}';
    }
  }
}
