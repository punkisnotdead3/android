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
package com.android.tools.idea.npw.model

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.automotiveModule.generateAutomotiveModule
import com.android.tools.idea.npw.module.recipes.genericModule.generateGenericModule
import com.android.tools.idea.npw.module.recipes.thingsModule.generateThingsModule
import com.android.tools.idea.npw.module.recipes.tvModule.generateTvModule
import com.android.tools.idea.npw.module.recipes.wearModule.generateWearModule
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.ProjectTemplateDataBuilder
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent

class ExistingProjectModelData(
  override var project: Project,
  override val projectSyncInvoker: ProjectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
) : ProjectModelData {
  override val applicationName: StringValueProperty = StringValueProperty(message("android.wizard.module.config.new.application"))
  override val packageName: StringValueProperty = StringValueProperty()
  override val projectLocation: StringValueProperty = StringValueProperty(project.basePath!!)
  override val useAppCompat = BoolValueProperty()
  override val useGradleKts = BoolValueProperty(project.hasKtsUsage())
  override val isNewProject = false
  override val language: OptionalValueProperty<Language> = OptionalValueProperty(getInitialSourceLanguage(project))
  override val multiTemplateRenderer: MultiTemplateRenderer = MultiTemplateRenderer { renderer ->
    object : Task.Modal(project, message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        renderer(project)
      }
    }.queue()
    projectSyncInvoker.syncProject(project)
  }
  override val projectTemplateDataBuilder = ProjectTemplateDataBuilder(false)

  init {
    applicationName.addConstraint(String::trim)
  }
}

interface ModuleModelData : ProjectModelData {
  val template: ObjectProperty<NamedModuleTemplate>
  val formFactor: ObjectProperty<FormFactor>
  val isLibrary: Boolean
  val moduleName: StringValueProperty
  /**
   * A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
   * module, or instead modifies an existing module (for example just adding a new Activity)
   */
  val androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem>
  val moduleTemplateDataBuilder: ModuleTemplateDataBuilder
}

class NewAndroidModuleModel(
  projectModelData: ProjectModelData,
  template: NamedModuleTemplate,
  moduleParent: String,
  override val formFactor: ObjectProperty<FormFactor>,
  commandName: String = "New Module",
  override val isLibrary: Boolean = false
) : ModuleModel(
  "",
  commandName,
  isLibrary,
  projectModelData,
  template,
  moduleParent
) {
  override val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(projectTemplateDataBuilder, true)
  override val renderer = ModuleTemplateRenderer()

  val bytecodeLevel: OptionalProperty<BytecodeLevel> = OptionalValueProperty(getInitialBytecodeLevel())

  init {
    val msgId: String = when {
      isLibrary -> "android.wizard.module.config.new.library"
      else -> "android.wizard.module.config.new.application"
    }
    applicationName.set(message(msgId))
  }

  // TODO(qumeric): replace constructors by factories
  constructor(
    project: Project,
    moduleParent: String,
    projectSyncInvoker: ProjectSyncInvoker,
    template: NamedModuleTemplate,
    isLibrary: Boolean = false
  ) : this(
    projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
    template = template,
    moduleParent = moduleParent,
    formFactor = ObjectValueProperty(FormFactor.Mobile),
    isLibrary = isLibrary
  )

  constructor(
    projectModel: NewProjectModel,
    moduleParent: String,
    template: NamedModuleTemplate,
    formFactor: ObjectValueProperty<FormFactor> = ObjectValueProperty(FormFactor.Mobile)
  ) : this(
    projectModelData = projectModel,
    template = template,
    moduleParent = moduleParent,
    formFactor = formFactor
  ) {
    multiTemplateRenderer.incrementRenders()
  }

  inner class ModuleTemplateRenderer : ModuleModel.ModuleTemplateRenderer() {
    override val recipe: Recipe get() = when(formFactor.get()) {
      FormFactor.Mobile -> { data: TemplateData ->
        generateAndroidModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get(), bytecodeLevel.value)
      }
      FormFactor.Wear -> { data: TemplateData ->
        generateWearModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Automotive -> { data: TemplateData ->
        generateAutomotiveModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Tv -> { data: TemplateData ->
        generateTvModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Things -> { data: TemplateData ->
        generateThingsModule(data as ModuleTemplateData, applicationName.get(), useGradleKts.get())
      }
      FormFactor.Generic -> { data: TemplateData ->
        generateGenericModule(data as ModuleTemplateData)
      }
    }

    override val loggingEvent: AndroidStudioEvent.TemplateRenderer
      get() = formFactor.get().toModuleRenderingLoggingEvent()

    @WorkerThread
    override fun init() {
      super.init()

      moduleTemplateDataBuilder.apply {
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@NewAndroidModuleModel.packageName.get())
      }
      val tff = formFactor.get()
      projectTemplateDataBuilder.includedFormFactorNames.putIfAbsent(tff, mutableListOf(moduleName.get()))?.add(moduleName.get())
    }
  }

  override fun handleFinished() {
    super.handleFinished()
    saveWizardState()
  }

  private fun getInitialBytecodeLevel(): BytecodeLevel {
    if (isLibrary) {
      val savedValue = properties.getValue(PROPERTIES_BYTECODE_LEVEL_KEY)
      return BytecodeLevel.values().firstOrNull { it.toString() == savedValue } ?: BytecodeLevel.default
    }
    return BytecodeLevel.default
  }

  private fun saveWizardState() = with(properties) {
    if (isLibrary) {
      setValue(PROPERTIES_BYTECODE_LEVEL_KEY, bytecodeLevel.value.toString())
    }
  }
}

private fun FormFactor.toModuleRenderingLoggingEvent() = when(this) {
  FormFactor.Mobile -> RenderLoggingEvent.ANDROID_MODULE
  FormFactor.Tv -> RenderLoggingEvent.ANDROID_TV_MODULE
  FormFactor.Automotive -> RenderLoggingEvent.AUTOMOTIVE_MODULE
  FormFactor.Things -> RenderLoggingEvent.THINGS_MODULE
  FormFactor.Wear -> RenderLoggingEvent.ANDROID_WEAR_MODULE
  FormFactor.Generic -> RenderLoggingEvent.ANDROID_MODULE // TODO(b/145975555)
}

private fun Project.hasKtsUsage() : Boolean {
  // TODO(parentej): Check if settings is kts or any module is kts
  return false
}
