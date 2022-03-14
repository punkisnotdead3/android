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
package com.android.tools.idea.gradle.project.sync

import com.android.Version
import com.android.builder.model.AndroidProject
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.composites.BuildMap
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeUnresolvedDependency
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.buildId
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.projectPath
import com.android.tools.idea.gradle.model.variant
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.BEFORE_MINIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW
import com.android.tools.idea.gradle.project.upgrade.computeAndroidGradlePluginCompatibility
import com.android.utils.appendCapitalized
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File
import java.util.LinkedList
import com.android.builder.model.v2.ide.Variant as V2Variant
import com.android.builder.model.v2.models.AndroidProject as V2AndroidProject
import com.android.builder.model.v2.models.ProjectSyncIssues as V2ProjectSyncIssues

internal class AndroidExtraModelProviderWorker(
  controller: BuildController,
  private val syncOptions: SyncActionOptions,
  private val buildModels: List<GradleBuild>, // Always not empty.
  private val consumer: ProjectImportModelProvider.BuildModelConsumer
) {
  private val androidModulesById: MutableMap<String, AndroidModule> = HashMap()
  private val buildFolderPaths = ModelConverter.populateModuleBuildDirs(
    controller)
  private val maybeParallelActionRunner = createActionRunner(controller, syncOptions.flags.studioFlagParallelSyncEnabled)
  private val sequentialActionRunner = createActionRunner(controller, false)
  private var canFetchV2Models: Boolean? = null
  private val modelCache: ModelCache by lazy {
    ModelCache.create(canFetchV2Models ?: error("Unable to determine which modelCache should be used"), buildFolderPaths)
  }

  fun populateBuildModels() {
    try {
      val modules: List<GradleModule> =
        when (syncOptions) {
          is SyncProjectActionOptions -> {
            populateAndroidModels(syncOptions, getBasicIncompleteGradleModules())
          }
          is NativeVariantsSyncActionOptions -> {
            consumer.consume(
              buildModels.first(),
              // TODO(b/215344823): Idea parallel model fetching is broken for now, so we need to request it sequentially.
              sequentialActionRunner.runAction { controller -> controller.getModel(IdeaProject::class.java) },
              IdeaProject::class.java
            )
            fetchNativeVariantsAndroidModels(syncOptions)
          }
          // Note: No more cases.
        }
      modules.forEach { it.deliverModels(consumer) }
    }
    catch (e: AndroidSyncException) {
      consumer.consume(
        buildModels.first(),
        IdeAndroidSyncError(e.message.orEmpty(), e.stackTrace.map { it.toString() }),
        IdeAndroidSyncError::class.java
      )
    }
  }

  data class ExtendedVariantData(
    val basicVariant: BasicVariant,
    val variant: V2Variant,
    val dependencies: VariantDependencies
  )

  sealed class AndroidProjectResult {
    class V1Project(val modelCache: ModelCache.V1, androidProject: AndroidProject) : AndroidProjectResult() {
      override val buildName: String? = null
      override val agpVersion: String = safeGet(androidProject::getModelVersion, "")
      override val ideAndroidProject: IdeAndroidProjectImpl = modelCache.androidProjectFrom(androidProject)
      override val allVariantNames: Set<String> = safeGet(androidProject::getVariantNames, null).orEmpty().toSet()
      override val defaultVariantName: String? = safeGet(androidProject::getDefaultVariant, null)
                                                 ?: allVariantNames.getDefaultOrFirstItem("debug")
      override val syncIssues: Collection<SyncIssue>? = @Suppress("DEPRECATION") safeGet(androidProject::getSyncIssues, null)
      override val variantNameResolver: VariantNameResolver = fun(_: String?, _: (String) -> String?): String? = null
      val ndkVersion: String? = safeGet(androidProject::getNdkVersion, null)

      override fun createVariantFetcher(): IdeVariantFetcher = v1VariantFetcher(modelCache)
    }

    class V2Project(
      val modelCache: ModelCache.V2,
      basicAndroidProject: BasicAndroidProject,
      androidProject: V2AndroidProject,
      val modelVersions: Versions,
      androidDsl: AndroidDsl
    ) : AndroidProjectResult() {
      override val buildName: String = basicAndroidProject.buildName
      override val agpVersion: String = modelVersions.agp
      override val ideAndroidProject: IdeAndroidProjectImpl =
        modelCache.androidProjectFrom(basicAndroidProject, androidProject, modelVersions, androidDsl)
      val basicVariants: List<BasicVariant> = basicAndroidProject.variants.toList()
      val v2Variants: List<IdeVariantCoreImpl> = let {
        val v2Variants: List<V2Variant> = androidProject.variants.toList()
        val basicVariantMap = basicVariants.associateBy { it.name }

        v2Variants.map {
          modelCache.variantFrom(
            androidProject = ideAndroidProject,
            basicVariant = basicVariantMap[it.name] ?: error("BasicVariant not found. Name: ${it.name}"),
            variant = it,
            modelVersion = GradleVersion.tryParseAndroidGradlePluginVersion(agpVersion)
          )
        }
      }

      override val allVariantNames: Set<String> = basicVariants.map { it.name }.toSet()
      override val defaultVariantName: String? =
        // Try to get the default variant based on default BuildTypes and productFlavors, otherwise get first one in the list.
        basicVariants.getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors)
      override val syncIssues: Collection<SyncIssue>? = null
      override val variantNameResolver: VariantNameResolver = buildVariantNameResolver(ideAndroidProject, v2Variants)

      override fun createVariantFetcher(): IdeVariantFetcher = v2VariantFetcher(modelCache, v2Variants)
    }

    abstract val buildName: String?
    abstract val agpVersion: String
    abstract val ideAndroidProject: IdeAndroidProjectImpl
    abstract val allVariantNames: Set<String>
    abstract val defaultVariantName: String?
    abstract val syncIssues: Collection<SyncIssue>?
    abstract val variantNameResolver: VariantNameResolver
    abstract fun createVariantFetcher(): IdeVariantFetcher
  }

  private fun canFetchV2Models(gradlePluginVersion: GradleVersion?): Boolean {
    return gradlePluginVersion != null && gradlePluginVersion.isAtLeast(7, 2, 0, "alpha", 1, true)
  }

  /**
   * Checks if we can request the V2 models in parallel.
   * We need to make sure we only request the models in parallel if:
   * - we are fetching android models
   * - and using at least AGP 7.3.0-alpha-04.
   *  @returns true if we can fetch the V2 models in parallel, otherwise, returns false.
   */
  private fun canUseParallelSync(gradlePluginVersion: GradleVersion?): Boolean {
    return gradlePluginVersion != null && gradlePluginVersion.isAtLeast(7, 3, 0, "alpha", 4, true)
  }

  private fun getBasicIncompleteGradleModules(): List<BasicIncompleteGradleModule> {
    return maybeParallelActionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): BasicIncompleteGradleModule {
          // Request V2 models if flag is enabled.
          if (syncOptions.flags.studioFlagUseV2BuilderModels) {
            // First request the Versions model to make sure we can fetch V2 models.
            val versions = controller.findNonParameterizedV2Model(gradleProject, Versions::class.java)
            if (versions?.also { checkAgpVersionCompatibility(versions.agp, syncOptions) } != null &&
                canFetchV2Models(GradleVersion.tryParseAndroidGradlePluginVersion(versions.agp))) {
              // This means we can request V2.
              return BasicV2AndroidModuleGradleProject(gradleProject, versions)
            }
          }
          // We cannot request V2 models.
          return BasicNonV2IncompleteGradleModule(gradleProject)
        }
      }.toList()
    )
  }

  /**
   * Requests Android project models for the given [buildModels]
   *
   * We do this by going through each module and query Gradle for the following models:
   *   1. Query for the AndroidProject for the module
   *   2. Query for the NativeModule (V2 API) (only if we also obtain an AndroidProject)
   *   3. Query for the NativeAndroidProject (V1 API) (only if we can obtain AndroidProject but cannot obtain V2 NativeModule)
   *   4. Query for the GlobalLibraryMap for the module (we ALWAYS do this regardless of the other two models)
   *   5. (Single Variant Sync only) Work out which variant for which models we need to request, and request them.
   *      See IdeaSelectedVariantChooser for more details.
   *
   * If single variant sync is enabled then [findParameterizedAndroidModel] will use Gradle parameterized model builder API
   * in order to stop Gradle from building the variant.
   * All the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  private fun populateAndroidModels(
    syncOptions: SyncProjectActionOptions,
    basicIncompleteModules: List<BasicIncompleteGradleModule>
  ): List<GradleModule> {
    val buildNameMap =
      (buildModels
         .mapNotNull { build ->
           maybeParallelActionRunner.runAction { controller -> controller.findModel(build.rootProject, BuildMap::class.java) }
         }
         .flatMap { buildNames -> buildNames.buildIdMap.entries.map { it.key to it.value } } +
       (":" to buildFolderPaths.buildRootDirectory!!)
      )
        .toMap()

    val canFetchModelsInParallel = basicIncompleteModules
      .filterIsInstance<BasicV2AndroidModuleGradleProject>()
      .all {
        canUseParallelSync(GradleVersion.tryParseAndroidGradlePluginVersion(it.versions.agp))
      }

    canFetchV2Models = basicIncompleteModules.filterIsInstance<BasicV2AndroidModuleGradleProject>().isNotEmpty()

    val v2AndroidModules = fetchV2AndroidModulesAction(
      basicIncompleteModules.filterIsInstance<BasicV2AndroidModuleGradleProject>(), buildNameMap, canFetchModelsInParallel
    )

    val otherGradleModules  =
      fetchGradleModulesSequentialAction(basicIncompleteModules.filterIsInstance<BasicNonV2IncompleteGradleModule>(), buildNameMap)

    // Merge models requested through parallel Sync and not.
    val modules: List<GradleModule> = v2AndroidModules + otherGradleModules

    modules.filterIsInstance<AndroidModule>().forEach { androidModulesById[it.id] = it }

    val variantNameResolvers = modules
      .associate { (it.gradleProject.projectIdentifier.buildIdentifier.rootDir to it.gradleProject.path) to it.variantNameResolver }

    fun getVariantNameResolver(buildId: File, projectPath: String): VariantNameResolver =
      variantNameResolvers.getOrElse(buildId to projectPath) {
        error("Project identifier cannot be resolved. BuildName: $buildId, ProjectPath: $projectPath")
      }

    when (syncOptions) {
      is SingleVariantSyncActionOptions -> {
        // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
        // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
        // e.g the ones that should be selected by the IDE.
        chooseSelectedVariants(modules.filterIsInstance<AndroidModule>(), syncOptions, canFetchModelsInParallel, ::getVariantNameResolver)
      }
      is AllVariantsSyncActionOptions -> {
        syncAllVariants(modules.filterIsInstance<AndroidModule>(), syncOptions, canFetchModelsInParallel, ::getVariantNameResolver)
      }
    }

    // AdditionalClassifierArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
    getAdditionalClassifierArtifactsModel(
      maybeParallelActionRunner,
      modules.filterIsInstance<AndroidModule>(),
      syncOptions.additionalClassifierArtifactsAction.cachedLibraries,
      syncOptions.additionalClassifierArtifactsAction.downloadAndroidxUISamplesSources
    )


    // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces additional issues.
    // Note that "last" here means last among Android models since many non-Android models are requested after this point.
    populateProjectSyncIssues(modules.filterIsInstance<AndroidModule>(), canFetchModelsInParallel)

    return modules
  }

  private fun fetchGradleModulesSequentialAction(
    basicIncompleteGradleModules: List<BasicNonV2IncompleteGradleModule>,
    buildNameMap: Map<String, File>
  ): List<GradleModule> {

    return sequentialActionRunner.runActions(
      basicIncompleteGradleModules.map {
        fun(controller: BuildController): GradleModule {
          val androidProject = controller.findParameterizedAndroidModel(
            it.gradleProject,
            AndroidProject::class.java,
            shouldBuildVariant = false
          )
          if (androidProject?.also { checkAgpVersionCompatibility(androidProject.modelVersion, syncOptions) } != null) {
            val androidProjectResult = AndroidProjectResult.V1Project(modelCache as ModelCache.V1, androidProject)

            val nativeModule = controller.getNativeModuleFromGradle(it.gradleProject, syncAllVariantsAndAbis = false)
            val nativeAndroidProject: NativeAndroidProject? =
              if (nativeModule == null)
                controller.findParameterizedAndroidModel(it.gradleProject, NativeAndroidProject::class.java, shouldBuildVariant = false)
              else null

            return createAndroidModule(
              it.gradleProject,
              androidProjectResult,
              nativeAndroidProject,
              nativeModule,
              buildNameMap,
              modelCache
            )
          }

          val kotlinGradleModel = controller.findModel(it.gradleProject, KotlinGradleModel::class.java)
          val kaptGradleModel = controller.findModel(it.gradleProject, KaptGradleModel::class.java)
          return JavaModule(it.gradleProject, kotlinGradleModel, kaptGradleModel)
        }
      }.toList()
    )
  }

  private fun fetchV2AndroidModulesAction(
    v2IncompleteBasicModules: List<BasicV2AndroidModuleGradleProject>,
    buildNameMap: Map<String, File>,
    canFetchV2inParallel: Boolean
  ): List<AndroidModule> {
    val v2ModelActionRunner  =  when (canFetchV2inParallel) {
      true -> maybeParallelActionRunner
      false -> sequentialActionRunner
    }
    return v2ModelActionRunner.runActions(
      v2IncompleteBasicModules.map {
        fun(controller: BuildController): AndroidModule? {
          val basicAndroidProject = controller.findNonParameterizedV2Model(it.gradleProject, BasicAndroidProject::class.java)
          val androidProject = controller.findNonParameterizedV2Model(it.gradleProject, V2AndroidProject::class.java)
          val androidDsl = controller.findNonParameterizedV2Model(it.gradleProject, AndroidDsl::class.java)

          if (basicAndroidProject != null && androidProject != null && androidDsl != null)  {
            val androidProjectResult =
              AndroidProjectResult.V2Project(modelCache as ModelCache.V2, basicAndroidProject, androidProject, it.versions, androidDsl)

            // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
            val nativeModule = controller.getNativeModuleFromGradle(it.gradleProject, syncAllVariantsAndAbis = false)
            val nativeAndroidProject: NativeAndroidProject? =
              if (nativeModule == null)
                controller.findParameterizedAndroidModel(it.gradleProject, NativeAndroidProject::class.java, shouldBuildVariant = false)
              else null

            return createAndroidModule(
              it.gradleProject,
              androidProjectResult,
              nativeAndroidProject,
              nativeModule,
              buildNameMap,
              modelCache
            )
          }

          return null
        }
      }.toList()
    ).filterNotNull()
  }

  private fun checkAgpVersionCompatibility(agpVersionString: String?, syncOptions: SyncActionOptions) {
    val agpVersion = if (agpVersionString != null) GradleVersion.parse(agpVersionString) else return
    val latestKnown = GradleVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    when (computeAndroidGradlePluginCompatibility(agpVersion, latestKnown)) {
      // We want to report to the user that they are using an AGP version that is below the minimum supported version for Android Studio,
      // and this is regardless of whether we want to trigger the upgrade assistant or not. Sync should always fail here.
      BEFORE_MINIMUM -> throw AgpVersionTooOld(agpVersion)
      DIFFERENT_PREVIEW -> if (!syncOptions.flags.studioFlagDisableForcedUpgrades) throw AgpVersionIncompatible(agpVersion)
      COMPATIBLE -> Unit
    }
  }

  private fun fetchNativeVariantsAndroidModels(
    syncOptions: NativeVariantsSyncActionOptions
  ): List<GradleModule> {
    val modelCache = ModelCache.create(false)
    val nativeModules = maybeParallelActionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): GradleModule? {
          val projectIdentifier = gradleProject.projectIdentifier
          val moduleId = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, projectIdentifier.projectPath)
          val variantName = syncOptions.moduleVariants[moduleId] ?: return null

          fun tryV2(): NativeVariantsAndroidModule? {
            controller.findModel(gradleProject, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
              it.variantsToGenerateBuildInformation = listOf(variantName)
              it.abisToGenerateBuildInformation = syncOptions.requestedAbis.toList()
            } ?: return null
            return NativeVariantsAndroidModule.createV2(gradleProject)
          }

          fun fetchV1Abi(abi: String): IdeNativeVariantAbi? {
            val model = controller.findModel(
              gradleProject,
              NativeVariantAbi::class.java,
              ModelBuilderParameter::class.java
            ) { parameter ->
              parameter.setVariantName(variantName)
              parameter.setAbiName(abi)
            } ?: return null
            return modelCache.nativeVariantAbiFrom(model)
          }

          fun tryV1(): NativeVariantsAndroidModule {
            return NativeVariantsAndroidModule.createV1(gradleProject, syncOptions.requestedAbis.mapNotNull { abi -> fetchV1Abi(abi) })
          }

          return tryV2() ?: tryV1()
        }
      }.toList()
    ).filterNotNull()

    populateProjectSyncIssues(nativeModules, false)

    return nativeModules
  }

  private fun populateProjectSyncIssues(androidModules: List<GradleModule>, canFetchModelsInParallel: Boolean) {
    if (androidModules.isEmpty()) return
    val syncIssuesActionRunner = when (canFetchModelsInParallel) {
      true -> maybeParallelActionRunner
      false -> sequentialActionRunner
    }

    syncIssuesActionRunner.runActions(
      androidModules.map { module ->
        fun(controller: BuildController) {
          val syncIssues = if (syncOptions.flags.studioFlagUseV2BuilderModels && (module is AndroidModule) &&
                               canFetchV2Models(module.agpVersion)) {
            // Request V2 sync issues.
            controller.findModel(module.findModelRoot, V2ProjectSyncIssues::class.java)?.syncIssues?.toV2SyncIssueData()
          } else {
            controller.findModel(module.findModelRoot, ProjectSyncIssues::class.java)?.syncIssues?.toSyncIssueData()
          }

          // For V2: we do not populate SyncIssues with Unresolved dependencies because we pass them through builder models.
          val v2UnresolvedDependenciesIssues = if (module is AndroidModule) {
            module.unresolvedDependencies.map {
              IdeSyncIssueImpl(
                message = "Unresolved dependencies",
                data = it.name,
                multiLineMessage = it.cause?.lines(),
                severity = IdeSyncIssue.SEVERITY_ERROR,
                type = IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY
              ) }
          } else {
            emptyList()
          }

          if (syncIssues != null) {
            module.setSyncIssues(syncIssues + v2UnresolvedDependenciesIssues)
          }
        }
      }
    )
  }

  /**
   * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [BasicGradleProject].
   */
  private fun <T> BuildController.findParameterizedAndroidModel(
    project: BasicGradleProject,
    modelType: Class<T>,
    shouldBuildVariant: Boolean
  ): T? {
    if (!shouldBuildVariant) {
      try {
        val model = getModel(project, modelType, ModelBuilderParameter::class.java) { parameter ->
          parameter.shouldBuildVariant = false
        }
        if (model != null) return model
      }
      catch (e: UnsupportedVersionException) {
        // Using old version of Gradle. Fall back to all variants sync for this module.
      }
    }
    return findModel(project, modelType)
  }

  /**
   * Gets the [V2AndroidProject] or [ModelVersions] (based on [modelType]) for the given [BasicGradleProject].
   */
  private fun <T> BuildController.findNonParameterizedV2Model(
    project: BasicGradleProject,
    modelType: Class<T>
  ): T? {
    return findModel(project, modelType)
  }

  private fun BuildController.getNativeModuleFromGradle(project: BasicGradleProject, syncAllVariantsAndAbis: Boolean): NativeModule? {
    return try {
      if (!syncAllVariantsAndAbis) {
        // With single variant mode, we first only collect basic project information. The more complex information will be collected later
        // for the selected variant and ABI.
        getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = emptyList()
          it.abisToGenerateBuildInformation = emptyList()
        }
      }
      else {
        // If single variant is not enabled, we sync all variant and ABIs at once.
        getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = null
          it.abisToGenerateBuildInformation = null
        }
      }
    }
    catch (e: UnsupportedVersionException) {
      // Using old version of Gradle that does not support V2 models.
      null
    }
  }

  private fun BuildController.isKotlinMppProject(root: Model) = findModel(root, KotlinMPPGradleModel::class.java) != null

  private fun BuildController.findKotlinGradleModelForAndroidProject(root: Model, variantName: String): KotlinGradleModel? {
    // Do not apply single-variant sync optimization to Kotlin multi-platform projects. We do not know the exact set of source sets
    // that needs to be processed.
    return if (isKotlinMppProject(root)) findModel(root, KotlinGradleModel::class.java)
    else findModel(root, KotlinGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
      it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
    }
  }

  private fun BuildController.findKaptGradleModelForAndroidProject(root: Model, variantName: String): KaptGradleModel? {
    // Do not apply single-variant sync optimization to Kotlin multi-platform projects. We do not know the exact set of source sets
    // that needs to be processed.
    return if (isKotlinMppProject(root)) findModel(root, KaptGradleModel::class.java)
    else findModel(root, KaptGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
      it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
    }
  }

  /**
   * This method requests all of the required [Variant] models from Gradle via the tooling API.
   *
   * Function for choosing the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
   * works as follows:
   *  1. For Android app modules using new versions of the plugin, the [Variant] to select is obtained from the model.
   *     For older plugins, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
   *     variant names alphabetically and picks the first one.
   *  2. For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
   *     depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
   *     (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.
   */
  fun chooseSelectedVariants(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions,
    canFetchModelsInParallel: Boolean,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver
  ) {
    if (inputModules.isEmpty()) return
    val variantActionRunner = when (canFetchModelsInParallel) {
      true -> maybeParallelActionRunner
      false -> sequentialActionRunner
      else -> error("Cannot determine if parallel model fetching can be used or not")
    }
    val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules, syncOptions)

    // When re-syncing a project without changing the selected variants it is likely that the selected variants won't in the end.
    // However, variant resolution is not perfectly parallelizable. To overcome this we try to fetch the previously selected variant
    // models in parallel and discard any that happen to change.
    val preResolvedVariants =
      when {
        !syncOptions.flags.studioFlagParallelSyncEnabled -> emptyMap()
        !syncOptions.flags.studioFlagParallelSyncPrefetchVariantsEnabled -> emptyMap()
        !variantActionRunner.parallelActionsSupported -> emptyMap()
        // TODO(b/181028873): Predict changed variants and build models in parallel.
        syncOptions.moduleIdWithVariantSwitched != null -> emptyMap()
        else -> {
          variantActionRunner
            .runActions(allModulesToSetUp.map {
              getVariantAndModuleDependenciesAction(
                it,
                syncOptions.selectedVariants,
                getVariantNameResolver
              )
            })
            .filterNotNull()
            .associateBy { it.moduleConfiguration }
        }
      }

    // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
    // requests any other modules that can't be reached.
    var propagatedToModules: List<ModuleConfiguration>? = null
    val visitedModules = HashSet<String>()

    while (propagatedToModules != null || allModulesToSetUp.isNotEmpty()) {
      // Walk the tree of module dependencies in the breadth-first-search order.
      val moduleConfigurationsToRequest: List<ModuleConfiguration> =
        if (propagatedToModules != null) {
          // If there are modules for which we know the selected variant, build models for these modules in parallel.
          val modules = propagatedToModules
          propagatedToModules = null
          modules.filter { visitedModules.add(it.id) }
        }
        else {
          // Otherwise take the next module from the main queue and follow its dependencies.
          val moduleConfiguration = allModulesToSetUp.removeFirst()
          if (!visitedModules.add(moduleConfiguration.id)) emptyList() else listOf(moduleConfiguration)
        }
      if (moduleConfigurationsToRequest.isEmpty()) continue

      val actions =
        moduleConfigurationsToRequest.map { moduleConfiguration ->
          val prefetchedModel = preResolvedVariants[moduleConfiguration]
          if (prefetchedModel != null) {
            // Return an action that simply returns the `prefetchedModel`.
            fun(_: BuildController) = prefetchedModel
          }
          else {
            getVariantAndModuleDependenciesAction(
              moduleConfiguration,
              syncOptions.selectedVariants,
              getVariantNameResolver
            )
          }
        }

      val preModuleDependencies = variantActionRunner.runActions(actions)

      // TODO(b/220325253): Kotlin parallel model fetching is broken.
      sequentialActionRunner.runActions(preModuleDependencies.mapNotNull { syncResult ->
        fun(controller: BuildController) {
          if (syncResult == null) return
          syncResult.module.kotlinGradleModel = controller.findKotlinGradleModelForAndroidProject(syncResult.module.findModelRoot, syncResult.ideVariant.name)
          syncResult.module.kaptGradleModel = controller.findKaptGradleModelForAndroidProject(syncResult.module.findModelRoot, syncResult.ideVariant.name)
        }
      }
      )

      preModuleDependencies.filterNotNull().forEach { result ->
        result.module.syncedVariant = result.ideVariant
        result.module.unresolvedDependencies = result.unresolvedDependencies
        result.module.syncedNativeVariant = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi
          is NativeVariantAbiResult.V2 -> null
          NativeVariantAbiResult.None -> null
        }
        result.module.syncedNativeVariantAbiName = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi.abi
          is NativeVariantAbiResult.V2 -> nativeVariantAbiResult.selectedAbiName
          NativeVariantAbiResult.None -> null
        }
      }
      propagatedToModules = preModuleDependencies.filterNotNull().flatMap { it.moduleDependencies }.takeUnless { it.isEmpty() }
    }
  }

  private fun prepareRequestedOrDefaultModuleConfigurations(
    inputModules: List<AndroidModule>,
    syncOptions: SingleVariantSyncActionOptions
  ): LinkedList<ModuleConfiguration> {
    // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
    // adding this module to the head of allModules so that its dependency modules are resolved first.
    return inputModules
      .asSequence()
      .mapNotNull { module -> selectedOrDefaultModuleConfiguration(module, syncOptions)?.let { module to it } }
      .sortedBy { (module, _) ->
        when {
          module.id == syncOptions.moduleIdWithVariantSwitched -> 0
          // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
          // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
          // dependencies of others and will be visited sooner and the configurations created below will be discarded. This is fine since
          // `createRequestedModuleConfiguration()` is cheap.
          module.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP -> 1
          else -> 2
        }
      }
      .map { it.second }
      .toCollection(LinkedList<ModuleConfiguration>())
  }

  private fun selectedOrDefaultModuleConfiguration(
    module: AndroidModule,
    syncOptions: SingleVariantSyncActionOptions
  ): ModuleConfiguration? {
    // TODO(b/181028873): Better predict variants that needs to be prefetched. Add a new field to record the predicted variant.
    val selectedVariants = syncOptions.selectedVariants
    val requestedVariantName = selectVariantForAppOrLeaf(module, selectedVariants) ?: return null
    val requestedAbi = selectedVariants.getSelectedAbi(module.id)
    return ModuleConfiguration(module.id, requestedVariantName, requestedAbi)
  }

  private fun selectVariantForAppOrLeaf(
    androidModule: AndroidModule,
    selectedVariants: SelectedVariants
  ): String? {
    val variantNames = androidModule.allVariantNames ?: return null
    return selectedVariants
             .getSelectedVariant(androidModule.id)
             // Check to see if we have a variant selected in the IDE, and that it is still a valid one.
             ?.takeIf { variantNames.contains(it) }
           ?: androidModule.defaultVariantName
  }

  fun syncAllVariants(
    inputModules: List<AndroidModule>,
    syncOptions: AllVariantsSyncActionOptions,
    canFetchModelsInParallel: Boolean,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver
  ) {
    if (inputModules.isEmpty()) return
    val variantActionRunner = when (canFetchModelsInParallel) {
      true -> maybeParallelActionRunner
      false -> sequentialActionRunner
      else -> error("Cannot determine if parallel model fetching can be used or not")
    }

    val variants =
      variantActionRunner
        .runActions(inputModules.flatMap { module ->
          module.allVariantNames.orEmpty().map { variant ->
            getVariantAction(ModuleConfiguration(module.id, variant, abi = null), variantNameResolvers)
          }
        })
        .filterNotNull()
        .groupBy { it.module }

    // TODO(b/220325253): Kotlin parallel model fetching is broken.
    sequentialActionRunner.runActions(variants.entries.map { (module, result) ->
      fun(controller: BuildController) {
        result.map {
          module.kotlinGradleModel = controller.findKotlinGradleModelForAndroidProject(module.findModelRoot, it.ideVariant.name)
          module.kaptGradleModel = controller.findKaptGradleModelForAndroidProject(module.findModelRoot, it.ideVariant.name)
        }
      }
    })

    variants.entries.forEach { (module, result) ->
      module.allVariants = result.map {it.ideVariant}
    }
  }

  private class SyncVariantResultCore(
    val moduleConfiguration: ModuleConfiguration,
    val module: AndroidModule,
    val ideVariant: IdeVariantCoreImpl,
    val nativeVariantAbi: NativeVariantAbiResult,
    val unresolvedDependencies: List<IdeUnresolvedDependency>
  )

  private class SyncVariantResult(
    val core: SyncVariantResultCore,
    val moduleDependencies: List<ModuleConfiguration>
  ) {
    val moduleConfiguration: ModuleConfiguration get() = core.moduleConfiguration
    val module: AndroidModule get() = core.module
    val ideVariant: IdeVariantCoreImpl get() = core.ideVariant
    val nativeVariantAbi: NativeVariantAbiResult get() = core.nativeVariantAbi
    val unresolvedDependencies: List<IdeUnresolvedDependency> get() = core.unresolvedDependencies
  }

  private fun SyncVariantResultCore.getModuleDependencyConfigurations(selectedVariants: SelectedVariants): List<ModuleConfiguration> {
    val selectedVariantDetails = selectedVariants.selectedVariants[moduleConfiguration.id]?.details

    // Regardless of the current selection in the IDE we try to select the same ABI in all modules the "top" module depends on even
    // when intermediate modules do not have native code.
    val abiToPropagate = nativeVariantAbi.abi ?: moduleConfiguration.abi

    val newlySelectedVariantDetails = createVariantDetailsFrom(module.androidProject.flavorDimensions, ideVariant, nativeVariantAbi.abi)
    val variantDiffChange =
      VariantSelectionChange.extractVariantSelectionChange(from = newlySelectedVariantDetails, base = selectedVariantDetails)


    fun propagateVariantSelectionChangeFallback(dependencyModuleId: String): ModuleConfiguration? {
      val dependencyModule = androidModulesById[dependencyModuleId] ?: return null
      val dependencyModuleCurrentlySelectedVariant = selectedVariants.selectedVariants[dependencyModuleId]
      val dependencyModuleSelectedVariantDetails = dependencyModuleCurrentlySelectedVariant?.details

      val newSelectedVariantDetails = dependencyModuleSelectedVariantDetails?.applyChange(
        variantDiffChange ?: VariantSelectionChange.EMPTY, applyAbiMode = ApplyAbiSelectionMode.ALWAYS
      )
                                      ?: return null

      // Make sure the variant name we guessed in fact exists.
      if (dependencyModule.allVariantNames?.contains(newSelectedVariantDetails.name) != true) return null

      return ModuleConfiguration(dependencyModuleId, newSelectedVariantDetails.name, abiToPropagate)
    }

    fun generateDirectModuleDependencies(): List<ModuleConfiguration> {
      return (ideVariant.mainArtifact.dependencyCores.moduleDependencies
              + ideVariant.unitTestArtifact?.ideDependenciesCore?.moduleDependencies.orEmpty()
              + ideVariant.androidTestArtifact?.dependencyCores?.moduleDependencies.orEmpty()
              + ideVariant.testFixturesArtifact?.dependencyCores?.moduleDependencies.orEmpty()
             )
        .mapNotNull { moduleDependency ->
          val dependencyProject = moduleDependency.projectPath
          val dependencyModuleId = Modules.createUniqueModuleId(moduleDependency.buildId ?: "", dependencyProject)
          val dependencyVariant = moduleDependency.variant
          if (dependencyVariant != null) {
            ModuleConfiguration(dependencyModuleId, dependencyVariant, abiToPropagate)
          }
          else {
            propagateVariantSelectionChangeFallback(dependencyModuleId)
          }
        }
        .distinct()
    }

    /**
     * Attempt to propagate variant changes to feature modules. This is not guaranteed to be correct, but since we do not know what the
     * real dependencies of each feature module variant are we can only guess.
     */
    fun generateDynamicFeatureDependencies(): List<ModuleConfiguration> {
      val rootProjectGradleDirectory = module.gradleProject.projectIdentifier.buildIdentifier.rootDir
      return module.androidProject.dynamicFeatures.mapNotNull { featureModuleGradlePath ->
        val featureModuleId = Modules.createUniqueModuleId(rootProjectGradleDirectory, featureModuleGradlePath)
        propagateVariantSelectionChangeFallback(featureModuleId)
      }
    }

    return generateDirectModuleDependencies() + generateDynamicFeatureDependencies()
  }

  /**
   * Given a [moduleConfiguration] returns an action that fetches variant specific models for the given module+variant and returns the set
   * of [SyncVariantResult]s containing the fetched models and describing resolved module configurations for the given module.
   */
  private fun getVariantAndModuleDependenciesAction(
    moduleConfiguration: ModuleConfiguration,
    selectedVariants: SelectedVariants,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver
  ): (BuildController) -> SyncVariantResult? {
    val getVariantAction = getVariantAction(moduleConfiguration, getVariantNameResolver)
    return fun(controller: BuildController): SyncVariantResult? {
      val syncVariantResultCore = getVariantAction(controller) ?: return null
      return SyncVariantResult(
        syncVariantResultCore,
        syncVariantResultCore.getModuleDependencyConfigurations(selectedVariants),
      )
    }
  }

  private fun getVariantAction(
    moduleConfiguration: ModuleConfiguration,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver
  ): (BuildController) -> SyncVariantResultCore? {
    val module = androidModulesById[moduleConfiguration.id] ?: return { null }
    return fun(controller: BuildController): SyncVariantResultCore {
      val abiToRequest: String?
      val nativeVariantAbi: NativeVariantAbiResult?
      val ideVariant: IdeVariantCoreImpl = module.variantFetcher(controller, variantNameResolvers, module, moduleConfiguration)
        ?: error("Resolved variant '${moduleConfiguration.variant}' does not exist.")
      val variantName = ideVariant.name

      abiToRequest = chooseAbiToRequest(module, variantName, moduleConfiguration.abi)
      nativeVariantAbi = abiToRequest?.let {
        controller.findNativeVariantAbiModel(modelCache, module, variantName, it) } ?: NativeVariantAbiResult.None

      fun getUnresolvedDependencies(): List<IdeUnresolvedDependency> {
         val unresolvedDependencies = mutableListOf<IdeUnresolvedDependency>()
        unresolvedDependencies.addAll(ideVariant.mainArtifact.unresolvedDependencies)
        ideVariant.androidTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        ideVariant.testFixturesArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        ideVariant.unitTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        return unresolvedDependencies
      }

      return SyncVariantResultCore(
        moduleConfiguration,
        module,
        ideVariant,
        nativeVariantAbi,
        getUnresolvedDependencies()
      )
    }
  }


  sealed class NativeVariantAbiResult {
    class V1(val variantAbi: IdeNativeVariantAbi) : NativeVariantAbiResult()
    class V2(val selectedAbiName: String) : NativeVariantAbiResult()
    object None : NativeVariantAbiResult()

    val abi: String? get() = when(this) {
      is V1 -> variantAbi.abi
      is V2 -> selectedAbiName
      None -> null
    }
  }

  private fun BuildController.findNativeVariantAbiModel(
    modelCache: ModelCache,
    module: AndroidModule,
    variantName: String,
    abiToRequest: String
  ): NativeVariantAbiResult {
    return if (module.nativeModelVersion == AndroidModule.NativeModelVersion.V2) {
      // V2 model is available, trigger the sync with V2 API
      // NOTE: Even though we drop the value returned the side effects of this code are important. Native sync creates file on the disk
      // which are later used.
      val model = findModel(module.findModelRoot, NativeModule::class.java, NativeModelBuilderParameter::class.java) { parameter ->
        parameter.variantsToGenerateBuildInformation = listOf(variantName)
        parameter.abisToGenerateBuildInformation = listOf(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V2(abiToRequest) else NativeVariantAbiResult.None
    }
    else {
      // Fallback to V1 models otherwise.
      val model = findModel(module.findModelRoot, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
        parameter.setVariantName(variantName)
        parameter.setAbiName(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V1(modelCache.nativeVariantAbiFrom(model)) else NativeVariantAbiResult.None
    }
  }

  /**
   * [selectedAbi] if null or the abi doesn't exist a default will be picked. This default will be "x86" if
   *               it exists in the abi names returned by the [NativeAndroidProject] otherwise the first item of this list will be
   *               chosen.
   */
  private fun chooseAbiToRequest(
    module: AndroidModule,
    variantName: String,
    selectedAbi: String?
  ): String? {
    // This module is not a native one, nothing to do
    if (module.nativeModelVersion == AndroidModule.NativeModelVersion.None) return null

    // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
    // Otherwise return from this method with a null result as abis are not supported.
    val abiNames = module.getVariantAbiNames(variantName) ?: return null

    return (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else abiNames.getDefaultOrFirstItem("x86"))
           ?: throw AndroidSyncException("No valid Native abi found to request!")
  }
}

private val List<GradleBuild>.projects: Sequence<BasicGradleProject> get() = asSequence().flatMap { it.projects.asSequence() }
private val androidArtifactSuffixes = listOf("", "unitTest", "androidTest")

private fun Collection<SyncIssue>.toSyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = safeGet(syncIssue::multiLineMessage, null)?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun Collection<com.android.builder.model.v2.ide.SyncIssue>.toV2SyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = safeGet(syncIssue::multiLineMessage, null)?.filterNotNull()?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun createAndroidModule(
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidExtraModelProviderWorker.AndroidProjectResult,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  buildNameMap: Map<String, File>,
  modelCache: ModelCache
): AndroidModule {
  val agpVersion: GradleVersion? = GradleVersion.tryParseAndroidGradlePluginVersion(androidProjectResult.agpVersion)

  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeAndroidProject = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project ->
      nativeAndroidProject?.let {
        modelCache.nativeAndroidProjectFrom(it, androidProjectResult.ndkVersion)
      }
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project ->
      if (nativeAndroidProject != null) {
        error("V2 models do not compatible with NativeAndroidProject. Please check your configuration.")
      } else {
        null
      }
  }
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule(
    agpVersion = agpVersion,
    buildName = androidProjectResult.buildName,
    buildNameMap = buildNameMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    variantNameResolver = androidProjectResult.variantNameResolver,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeAndroidProject = ideNativeAndroidProject,
    nativeModule = ideNativeModule
  )

  val syncIssues = androidProjectResult.syncIssues
  // It will be overridden if we receive something here but also a proper sync issues model later.
  if (syncIssues != null) androidModule.setSyncIssues(syncIssues.toSyncIssueData())

  return androidModule
}

private fun AndroidModule.adjustForTestFixturesSuffix(variantName: String): String {
  val allVariantNames = allVariantNames.orEmpty()
  val variantNameWithoutSuffix = variantName.removeSuffix("TestFixtures")
  return if (!allVariantNames.contains(variantName) && allVariantNames.contains(variantNameWithoutSuffix)) variantNameWithoutSuffix
  else variantName
}

private inline fun <T> safeGet(original: () -> T, default: T): T = try {
  original()
}
catch (ignored: UnsupportedOperationException) {
  default
}

private fun BuildController.findVariantModel(
  module: AndroidModule,
  variantName: String
): Variant? {
  return findModel(
    module.findModelRoot,
    Variant::class.java,
    ModelBuilderParameter::class.java
  ) { parameter ->
    parameter.setVariantName(variantName)
  }
}

/**
 * Valid only for [VariantDependencies] using model parameter for the given [BasicGradleProject] using .
 */
private fun BuildController.findVariantDependenciesV2Model(
  project: BasicGradleProject,
  variantName: String
): VariantDependencies? {
  return findModel(
    project,
    VariantDependencies::class.java,
    com.android.builder.model.v2.models.ModelBuilderParameter::class.java
  ) { it.variantName = variantName }
}

// Keep fetchers outside of AndroidProjectResult to avoid accidental references on larger builder models.
fun v1VariantFetcher(modelCache: ModelCache.V1): IdeVariantFetcher {
  return fun(
    controller: BuildController,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver,
    module: AndroidModule,
    configuration: ModuleConfiguration
  ): IdeVariantCoreImpl? {
    val androidModuleId = ModuleId(module.gradleProject.path, module.gradleProject.projectIdentifier.buildIdentifier.rootDir.path)
    val adjustedVariantName = module.adjustForTestFixturesSuffix(configuration.variant)
    val variant = controller.findVariantModel(module, adjustedVariantName) ?: return null
    return modelCache.variantFrom(module.androidProject, variant, module.agpVersion, androidModuleId)
  }
}

// Keep fetchers outside of AndroidProjectResult to avoid accidental references on larger builder models.
fun v2VariantFetcher(modelCache: ModelCache.V2, v2Variants: List<IdeVariantCoreImpl>): IdeVariantFetcher {
  return fun(
    controller: BuildController,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver,
    module: AndroidModule,
    configuration: ModuleConfiguration
  ): IdeVariantCoreImpl? {
    // In V2, we get the variants from AndroidModule.v2Variants.
    val variant = v2Variants?.firstOrNull { it.name == configuration.variant }
                  ?: error("Resolved variant '${configuration.variant}' does not exist.")

    // Request VariantDependencies model for the variant's dependencies.
    val variantDependencies = controller.findVariantDependenciesV2Model(module.gradleProject, configuration.variant) ?: return null
    return modelCache.variantFrom(
      variant,
      variantDependencies,
      variantNameResolvers,
      module.buildNameMap ?: error("Build name map not available for: ${module.id}")
    )
  }
}

