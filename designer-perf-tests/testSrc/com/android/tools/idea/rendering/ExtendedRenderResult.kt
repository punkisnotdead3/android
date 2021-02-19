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
package com.android.tools.idea.rendering

/**
 * Render result for perfgate tests to carry additional metrics.
 */
open class ExtendedRenderResult protected constructor(
  renderResult: RenderResult,
  val extendedStats: ExtendedStats) : RenderResult(
  renderResult.file,
  renderResult.module,
  renderResult.logger,
  renderResult.renderContext,
  renderResult.hasRequestedCustomViews(),
  renderResult.renderResult,
  renderResult.rootViews,
  renderResult.systemRootViews,
  renderResult.renderedImage,
  renderResult.defaultProperties,
  renderResult.defaultStyles,
  renderResult.validatorResult,
  renderResult.stats,
) {

  companion object {
    fun create(
      renderResult: RenderResult,
      firstExecuteCallbacksResult: ExecuteCallbacksResult,
      firstTouchEventResult: TouchEventResult,
      postTouchEventResult: ExecuteCallbacksResult) =
      ExtendedRenderResult(
        renderResult,
        ExtendedStats(
          firstExecuteCallbacksResult.durationMs,
          firstTouchEventResult.durationMs,
          postTouchEventResult.durationMs))
  }
}

/**
 * [firstExecuteCallbacksDurationMs] the duration in milliseconds of the very first call to execute android platform callbacks (Handler and
 * Choreographer). We expect it to take significant time because of class loading that happens the first time this code path is executed.
 * [firstTouchEventDurationMs] the duration in milliseconds of the very first call to triggering touch event against the android View and
 * Compose on touch infrastructure. We expect it to take significant time because of class loading that happens the first time this code
 * path is executed.
 * [postTouchEventDurationMs] the duration in milliseconds of the first call to execute android platform callbacks right after the first
 * touch event is fully propagated. We expect it to take significant time because touch event might add some new callbacks that are executed
 * the very first time and that might load classes.
 */
data class ExtendedStats(
  val firstExecuteCallbacksDurationMs: Long,
  val firstTouchEventDurationMs: Long,
  val postTouchEventDurationMs: Long
)

