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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.customizer.dependency.*;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static com.android.SdkConstants.FD_JARS;
import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.DOC;
import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.SOURCE;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.setModuleCompiledArtifact;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Sets the dependencies of a module imported from an {@link AndroidProject}.
 */
public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<AndroidGradleModel>
  implements BuildVariantModuleCustomizer<AndroidGradleModel> {

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel moduleModel, @NotNull AndroidGradleModel androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();

    DependencySet dependencies = Dependency.extractFrom(androidModel);
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      updateLibraryDependency(moduleModel, dependency, androidProject);
    }
    for (ModuleDependency dependency : dependencies.onModules()) {
      updateModuleDependency(moduleModel, dependency, androidProject);
    }

    addExtraSdkLibrariesAsDependencies(moduleModel, androidProject);

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(moduleModel.getProject());
    Collection<SyncIssue> syncIssues = androidModel.getSyncIssues();
    if (syncIssues != null) {
      messages.reportSyncIssues(syncIssues, moduleModel.getModule());
    }
    else {
      Collection<String> unresolvedDependencies = androidProject.getUnresolvedDependencies();
      messages.reportUnresolvedDependencies(unresolvedDependencies, moduleModel.getModule());
    }
  }

  private void updateModuleDependency(@NotNull ModifiableRootModel moduleModel,
                                      @NotNull ModuleDependency dependency,
                                      @NotNull AndroidProject androidProject) {
    ModuleManager moduleManager = ModuleManager.getInstance(moduleModel.getProject());
    Module moduleDependency = null;
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = module;
          break;
        }
      }
    }
    LibraryDependency compiledArtifact = dependency.getBackupDependency();

    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);
      orderEntry.setScope(dependency.getScope());

      if (compiledArtifact != null) {
        setModuleCompiledArtifact(moduleDependency, compiledArtifact);
      }
      return;
    }

    String backupName = compiledArtifact != null ? compiledArtifact.getName() : null;

    DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());
    setupErrors.addMissingModule(dependency.getGradlePath(), moduleModel.getModule().getName(), backupName);

    // fall back to library dependency, if available.
    if (compiledArtifact != null) {
      updateLibraryDependency(moduleModel, compiledArtifact, androidProject);
    }
  }

  public static void updateLibraryDependency(@NotNull ModifiableRootModel moduleModel,
                                             @NotNull LibraryDependency dependency,
                                             @NotNull AndroidProject androidProject) {
    Collection<String> binaryPaths = dependency.getPaths(BINARY);
    Collection<String> sourcePaths = dependency.getPaths(SOURCE);
    Collection<String> docPaths = dependency.getPaths(DOC);
    setUpLibraryDependency(moduleModel, dependency.getName(), dependency.getScope(), binaryPaths, sourcePaths, docPaths);

    File buildFolder = androidProject.getBuildFolder();

    // Exclude jar files that are in "jars" folder in "build" folder.
    // see https://code.google.com/p/android/issues/detail?id=123788
    ContentEntry[] contentEntries = moduleModel.getContentEntries();
    for (String binaryPath : binaryPaths) {
      File parent = new File(binaryPath).getParentFile();
      if (parent != null && FD_JARS.equals(parent.getName()) && isAncestor(buildFolder, parent, true)) {
        ContentEntry parentContentEntry = findParentContentEntry(parent, contentEntries);
        if (parentContentEntry != null) {
          parentContentEntry.addExcludeFolder(pathToIdeaUrl(parent));
        }
      }
    }
  }

  /**
   * Sets the 'useLibrary' libraries as dependencies.
   * <p>
   * These libraries are set at the project level, which makes it impossible to add them to a IDE SDK definition because the IDE SDK is
   * global to the whole IDE. To work around this limitation, we set these libraries as module dependencies instead.
   * </p>
   */
  private static void addExtraSdkLibrariesAsDependencies(@NotNull ModifiableRootModel moduleModel, @NotNull AndroidProject androidProject) {
    Sdk sdk = moduleModel.getSdk();
    assert sdk != null; // If we got here, SDK will *NOT* be null.

    Set<String> currentIdeSdkFilePaths = Sets.newHashSetWithExpectedSize(5);
    for (VirtualFile sdkFile : sdk.getRootProvider().getFiles(CLASSES)) {
      // We need to convert the VirtualFile to java.io.File, because the path of the VirtualPath is using 'jar' protocol and it won't match
      // the path returned by AndroidProject#getBootClasspath().
      File sdkFilePath = virtualToIoFile(sdkFile);
      currentIdeSdkFilePaths.add(sdkFilePath.getPath());
    }
    Collection<String> bootClasspath = androidProject.getBootClasspath();
    for (String library : bootClasspath) {
      if (isNotEmpty(library) && !currentIdeSdkFilePaths.contains(library)) {
        // Library is not in the SDK IDE definition. Add it as library and make the module depend on it.
        File binaryPath = new File(library);
        String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(library);
        // Include compile target as part of the name, to ensure the library name is unique to this Android platform.
        name = androidProject.getCompileTarget() + '.' + name;
        setUpLibraryDependency(moduleModel, name, DependencyScope.COMPILE, Collections.singletonList(library));
      }
    }
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @Override
  @NotNull
  public Class<AndroidGradleModel> getSupportedModelType() {
    return AndroidGradleModel.class;
  }
}
