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
package com.android.tools.idea.res;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.facet.ResourceFolderManager.ResourceFolderListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see ResourceRepositoryManager#getModuleResources()
 */
final class ModuleResourceRepository extends MultiResourceRepository implements SingleNamespaceResourceRepository {
  @NotNull
  private final AndroidFacet myFacet;

  @NotNull
  private final ResourceNamespace myNamespace;

  @NotNull
  private final SourceSet mySourceSet;

  @NotNull
  private final ResourceFolderRegistry myRegistry;

  @NotNull
  private final ResourceFolderManager myResourceFolderManager;

  @NotNull
  private final ResourceFolderListener myResourceFolderListener = new ResourceFolderListener() {
      @Override
      public void mainResourceFoldersChanged(@NotNull AndroidFacet facet,
                                             @NotNull List<? extends VirtualFile> folders,
                                             @NotNull Collection<? extends VirtualFile> added,
                                             @NotNull Collection<? extends VirtualFile> removed) {
        if (mySourceSet == SourceSet.MAIN) {
          updateRoots(folders);
        }
      }

      @Override
      public void testResourceFoldersChanged(@NotNull AndroidFacet facet,
                                             @NotNull List<? extends VirtualFile> folders,
                                             @NotNull Collection<? extends VirtualFile> added,
                                             @NotNull Collection<? extends VirtualFile> removed) {
        if (mySourceSet == SourceSet.TEST) {
          updateRoots(folders);
        }
      }
    };

  private enum SourceSet { MAIN, TEST }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository forMainResources(@NotNull AndroidFacet facet) {
    ResourceNamespace namespace = ResourceRepositoryManager.getInstance(facet).getNamespace();
    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);

    if (!facet.requiresAndroidModel()) {
      // Always just a single resource folder: simple.
      VirtualFile primaryResourceDir = ContainerUtil.getFirstItem(folderManager.getFolders(), null);
      if (primaryResourceDir == null) {
        return new EmptyRepository(namespace);
      }
      return resourceFolderRegistry.get(facet, primaryResourceDir);
    }

    List<VirtualFile> resourceDirectories = folderManager.getFolders();
    List<LocalResourceRepository> childRepositories = new ArrayList<>(1 + resourceDirectories.size());

    DynamicResourceValueRepository dynamicResources = DynamicResourceValueRepository.create(facet);
    childRepositories.add(dynamicResources);
    addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry);

    // We create a ModuleResourceRepository even if childRepositories.isEmpty(), because we may
    // dynamically add children to it later (in updateRoots).
    ModuleResourceRepository repository = new ModuleResourceRepository(facet, namespace, childRepositories, SourceSet.MAIN);
    Disposer.register(repository, dynamicResources);

    return repository;
  }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository forTestResources(@NotNull AndroidFacet facet) {
    ResourceNamespace namespace = ResourceRepositoryManager.getInstance(facet).getTestNamespace();
    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);

    if (!facet.requiresAndroidModel()) {
      // No test resources in legacy projects.
      return new EmptyRepository(namespace);
    }

    List<VirtualFile> resourceDirectories = folderManager.getTestFolders();
    List<LocalResourceRepository> childRepositories = new ArrayList<>(resourceDirectories.size());
    addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry);

    return new ModuleResourceRepository(facet, namespace, childRepositories, SourceSet.TEST);
  }

  /**
   * Inserts repositories for the given {@code resourceDirectories} into {@code childRepositories}, in the right order.
   *
   * <p>{@code resourceDirectories} is assumed to be in the order returned from
   * {@link IdeaSourceProvider#getCurrentSourceProviders(AndroidFacet)}, which is the inverse of what we need. The code in
   * {@link MultiResourceRepository#getMap(ResourceNamespace, ResourceType, boolean)} gives priority to child repositories which are earlier
   * in the list, so after creating repositories for every folder, we add them in reverse to the list.
   *
   * @param resourceDirectories directories for which repositories should be constructed
   * @param childRepositories the list of repositories to which new repositories will be added
   * @param facet {@link AndroidFacet} that repositories will correspond to
   * @param resourceFolderRegistry {@link ResourceFolderRegistry} used to construct the repositories
   */
  private static void addRepositoriesInReverseOverlayOrder(@NotNull List<VirtualFile> resourceDirectories,
                                                           @NotNull List<LocalResourceRepository> childRepositories,
                                                           @NotNull AndroidFacet facet,
                                                           @NotNull ResourceFolderRegistry resourceFolderRegistry) {
    for (int i = resourceDirectories.size(); --i >= 0;) {
      VirtualFile resourceDirectory = resourceDirectories.get(i);
      ResourceFolderRepository repository = resourceFolderRegistry.get(facet, resourceDirectory);
      childRepositories.add(repository);
    }
  }

  private ModuleResourceRepository(@NotNull AndroidFacet facet,
                                   @NotNull ResourceNamespace namespace,
                                   @NotNull List<? extends LocalResourceRepository> delegates,
                                   @NotNull SourceSet sourceSet) {
    super(facet.getModule().getName());
    myFacet = facet;
    myNamespace = namespace;
    mySourceSet = sourceSet;
    setChildren(delegates, ImmutableList.of());

    // Subscribe to update the roots when the resource folders change.
    myResourceFolderManager = ResourceFolderManager.getInstance(myFacet);
    myResourceFolderManager.addListener(myResourceFolderListener);

    myRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
  }

  @VisibleForTesting
  void updateRoots(List<? extends VirtualFile> resourceDirectories) {
    // Non-folder repositories to put in front of the list.
    List<LocalResourceRepository> other = null;

    // Compute current roots.
    Map<VirtualFile, ResourceFolderRepository> map = new HashMap<>();
    ImmutableList<LocalResourceRepository> children = getLocalResources();
    for (LocalResourceRepository repository : children) {
      if (repository instanceof ResourceFolderRepository) {
        ResourceFolderRepository folderRepository = (ResourceFolderRepository)repository;
        VirtualFile resourceDir = folderRepository.getResourceDir();
        map.put(resourceDir, folderRepository);
      }
      else {
        assert repository instanceof DynamicResourceValueRepository;
        if (other == null) {
          other = new ArrayList<>();
        }
        other.add(repository);
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed).
    Set<VirtualFile> newDirs = new HashSet<>(resourceDirectories);
    List<LocalResourceRepository> resources = new ArrayList<>(newDirs.size() + (other != null ? other.size() : 0));
    if (other != null) {
      resources.addAll(other);
    }

    for (VirtualFile dir : resourceDirectories) {
      ResourceFolderRepository repository = map.get(dir);
      if (repository == null) {
        repository = myRegistry.get(myFacet, dir);
      }
      else {
        map.remove(dir);
      }
      resources.add(repository);
    }

    if (resources.equals(children)) {
      // Nothing changed (including order); nothing to do
      assert map.isEmpty(); // shouldn't have created any new ones
      return;
    }

    for (ResourceFolderRepository removed : map.values()) {
      removed.removeParent(this);
    }

    setChildren(resources, Collections.emptyList());
  }

  @Override
  public void dispose() {
    super.dispose();

    myResourceFolderManager.removeListener(myResourceFolderListener);
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .addValue(mySourceSet)
      .toString();
  }

  @VisibleForTesting
  @NotNull
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                       @NotNull Collection<VirtualFile> resourceDirectories,
                                                       @NotNull ResourceNamespace namespace,
                                                       @Nullable DynamicResourceValueRepository dynamicResourceValueRepository) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<LocalResourceRepository> delegates =
        new ArrayList<>(resourceDirectories.size() + (dynamicResourceValueRepository == null ? 0 : 1));

    if (dynamicResourceValueRepository != null) {
      delegates.add(dynamicResourceValueRepository);
    }

    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    resourceDirectories.forEach(dir -> delegates.add(resourceFolderRegistry.get(facet, dir, namespace)));

    return new ModuleResourceRepository(facet, namespace, delegates, SourceSet.MAIN);
  }
}
