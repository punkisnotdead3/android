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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ActionMenuViewHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintHelperHandler;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceCategoryHandler;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceHandler;
import com.android.tools.idea.uibuilder.menu.MenuHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.google.common.base.Charsets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.concurrency.AppExecutorUtil;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Collection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

public class NlPaletteModel implements Disposable {
  @VisibleForTesting
  static final String THIRD_PARTY_GROUP = "3rd Party";
  static final String PROJECT_GROUP = "Project";

  /**
   * {@link Function} that returns all the classes within the project scope that inherit from android.view.View
   */
  private static final Function<Project, Query<PsiClass>> VIEW_CLASSES_QUERY = (project) -> {
    PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

    if (viewClass == null) {
      // There is probably no SDK
      return EmptyQuery.getEmptyQuery();
    }

    return ClassInheritorsSearch.search(viewClass, ProjectScope.getProjectScope(project), true);
  };

  /**
   * Data class which holds information about a custom view that we've extracted from its PsiClass.
   * We can use this information later to update the corresponding palette component without having
   * to hold onto the read lock.
   */
  private static class CustomViewInfo {
    public final String description, tagName, className;

    CustomViewInfo(String description, String tagName, String className) {
      this.description = description;
      this.tagName = tagName;
      this.className = className;
    }

    static Collection<CustomViewInfo> fromPsiClasses(Query<PsiClass> psiClasses) {
      ArrayList<CustomViewInfo> componentInfos = new ArrayList<>();

      psiClasses.forEach(psiClass -> {
        String description = psiClass.getName(); // We use the "simple" name as description on the preview.
        String tagName = psiClass.getQualifiedName();
        String className = PackageClassConverter.getQualifiedName(psiClass);

        if (description == null || tagName == null || className == null) {
          // Currently we ignore anonymous views
          return;
        }

        componentInfos.add(new CustomViewInfo(description, tagName, className));
      });

      return componentInfos;
    }
  }

  private final Map<NlLayoutType, Palette> myTypeToPalette;
  private final Module myModule;
  private UpdateListener myListener;

  @Override
  public void dispose() {
    myListener = null;
  }

  public interface UpdateListener {
    void update(@NotNull NlPaletteModel paletteModel, @NotNull NlLayoutType layoutType);
  }

  public static NlPaletteModel get(@NotNull AndroidFacet facet) {
    return facet.getModule().getComponent(NlPaletteModel.class);
  }

  private NlPaletteModel(@NotNull Module module) {
    myTypeToPalette = Collections.synchronizedMap(new EnumMap<>(NlLayoutType.class));
    myModule = module;
    Disposer.register(module, this);
  }

  @NotNull
  public Palette getPalette(@NotNull NlLayoutType type) {
    assert type.isSupportedByDesigner();
    Palette palette = myTypeToPalette.get(type);

    if (palette == null) {
      palette = loadPalette(type);
      saveInPaletteMap(type, palette);
      registerAdditionalComponents(type);
    }
    return palette;
  }

  private void saveInPaletteMap(@NotNull NlLayoutType type, @NotNull Palette palette) {
    myTypeToPalette.put(type, palette);
    notifyUpdateListener(type);
  }

  // Load 3rd party components asynchronously now and whenever a build finishes.
  private void registerAdditionalComponents(@NotNull NlLayoutType type) {
    loadAdditionalComponents(type, VIEW_CLASSES_QUERY);

    // Reload the additional components after every build to find new custom components
    AndroidProjectBuildNotifications
      .subscribe(myModule.getProject(), this, context -> loadAdditionalComponents(type, VIEW_CLASSES_QUERY));
  }

  public void setUpdateListener(@Nullable UpdateListener updateListener) {
    myListener = updateListener;
  }

  private void notifyUpdateListener(@NotNull NlLayoutType layoutType) {
    UpdateListener listener = myListener;
    if (listener != null) {
      ApplicationManager.getApplication().invokeLater(() -> listener.update(this, layoutType));
    }
  }

  @NotNull
  private Palette loadPalette(@NotNull NlLayoutType type) {
    try {
      String metadata = type.getPaletteFileName();
      URL url = NlPaletteModel.class.getResource(metadata);
      URLConnection connection = url.openConnection();

      try (Reader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8)) {
        return loadPalette(reader, type);
      }
    }
    catch (IOException | JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Loads all components that are not part of default palette description. This includes components from third party libraries and
   * components from the project.
   */
  @VisibleForTesting
  void loadAdditionalComponents(@NotNull NlLayoutType type,
                                @NotNull Function<Project, Query<PsiClass>> viewClasses) {
    Application application = ApplicationManager.getApplication();
    Project project = myModule.getProject();
    ReadAction
      .nonBlocking(() -> CustomViewInfo.fromPsiClasses(viewClasses.apply(project)))
      .expireWhen(() -> Disposer.isDisposed(this))
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess(viewInfos -> {
        // Right now we're still in the non-blocking read action. Schedule the follow-up work on another
        // background thread so we can avoid holding the read lock for longer than we need to.
        application.executeOnPooledThread(() -> replaceProjectComponents(type, viewInfos));
      })
      .onError(error -> {
        if (error instanceof ProcessCanceledException) {
          // Scheduling on the EDT guarantees that whatever write action preempted us will have completed when we try again.
          application.invokeLater(() -> loadAdditionalComponents(type, viewClasses));
        }
        else {
          getLogger().error(error);
        }
      });
  }

  private void replaceProjectComponents(@NotNull NlLayoutType type, Collection<CustomViewInfo> viewInfos) {
    // Reload the palette first
    Palette palette = loadPalette(type);

    viewInfos.forEach(viewInfo ->
      addAdditionalComponent(type, PROJECT_GROUP, palette, StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW,
                             viewInfo.tagName, viewInfo.className, null, null, "",
                             null, Collections.emptyList(), Collections.emptyList())
    );

    saveInPaletteMap(type, palette);
  }

  @VisibleForTesting
  @NotNull
  public Palette loadPalette(@NotNull Reader reader, @NotNull NlLayoutType type) throws JAXBException {
    Palette palette = Palette.parse(reader, ViewHandlerManager.get(myModule.getProject()));
    myTypeToPalette.put(type, palette);
    return palette;
  }

  /**
   * Adds a new component to the palette with the given properties. This method is used to add 3rd party and project components
   */
  @VisibleForTesting
  boolean addAdditionalComponent(@NotNull NlLayoutType type,
                                 @NotNull String groupName,
                                 @NotNull Palette palette,
                                 @Nullable Icon icon16,
                                 @NotNull String tagName,
                                 @NotNull String className,
                                 @Nullable @Language("XML") String xml,
                                 @Nullable @Language("XML") String previewXml,
                                 @NotNull String libraryCoordinate,
                                 @Nullable String preferredProperty,
                                 @NotNull List<String> properties,
                                 @NotNull List<String> layoutProperties) {
    if (tagName.indexOf('.') < 0 ||
        !NlComponentHelper.INSTANCE.viewClassToTag(tagName).equals(tagName) ||
        CONSTRAINT_LAYOUT.isEquals(tagName)) {
      // Do NOT allow third parties to overwrite predefined Google handlers
      return false;
    }

    ViewHandlerManager manager = ViewHandlerManager.get(myModule.getProject());
    ViewHandler handler = manager.createBuiltInHandler(tagName);

    // For now only support layouts
    if (type != NlLayoutType.LAYOUT ||
        handler instanceof PreferenceHandler ||
        handler instanceof PreferenceCategoryHandler ||
        handler instanceof MenuHandler ||
        handler instanceof ActionMenuViewHandler) {
      return false;
    }

    if (handler == null) { // no built in handler, let's create a delegate
      handler = manager.getHandlerOrDefault(tagName);

      if (handler instanceof ConstraintHelperHandler) {
        return false; // temporary hack
      }

      if (handler instanceof ViewGroupHandler) {
        handler = new CustomViewGroupHandler((ViewGroupHandler)handler, icon16, tagName, className, xml, previewXml,
                                             libraryCoordinate, preferredProperty, properties, layoutProperties);
      }
      else {
        handler = new CustomViewHandler(handler, icon16, tagName, className, xml, previewXml,
                                        libraryCoordinate, preferredProperty, properties);
      }
    }

    manager.registerHandler(tagName, handler);

    List<Palette.BaseItem> groups = palette.getItems();
    Palette.Group group = groups.stream()
      .filter(Palette.Group.class::isInstance)
      .map(Palette.Group.class::cast)
      .filter(g -> groupName.equals(g.getName()))
      .findFirst()
      .orElse(null);
    if (group == null) {
      group = new Palette.Group(groupName);
      groups.add(group);
    }
    Palette.Item item = new Palette.Item(tagName, handler);
    group.getItems().add(item);
    item.setUp(palette, manager);
    item.setParent(group);
    return true;
  }

  private static Logger getLogger() {
    return Logger.getInstance(NlPaletteModel.class);
  }
}
