/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.diagnostics.crash;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.util.Arrays;
import java.util.Map;

public class StudioExceptionReport extends BaseStudioReport {
  /**
   * {@link Throwable} classes with messages expected to be useful for debugging and not to contain PII.
   */
  private static final ImmutableList<Class<? extends Throwable>> THROWABLE_CLASSES_TO_TRACK_MESSAGES =
    ImmutableList.of(
      LinkageError.class,
      ReflectiveOperationException.class,
      ArrayIndexOutOfBoundsException.class,
      ClassCastException.class,
      IndexOutOfBoundsException.class,
      NoPiiException.class);

  public static final String KEY_EXCEPTION_INFO = "exception_info";

  @NonNull private final String exceptionInfo;
  @Nullable private final PluginId pluginId;

  private StudioExceptionReport(@Nullable String version,
                                @NonNull String exceptionInfo,
                                @Nullable PluginId pluginId,
                                @Nullable Map<String, String> productData) {
    super(version, productData, "Exception");
    this.exceptionInfo = exceptionInfo;
    this.pluginId = pluginId;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    builder.addTextBody(KEY_EXCEPTION_INFO, exceptionInfo);
    // Capture kotlin version for kotlin exceptions.
    if (isKotlinOnStack()) {
      builder.addTextBody("kotlinVersion", getKotlinPluginVersionDescription());
    }
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin != null) {
      builder.addTextBody("plugin.name", plugin.getName());
      builder.addTextBody("plugin.version", plugin.getVersion());
    }
  }

  private boolean isKotlinOnStack() {
    return exceptionInfo.contains("\tat org.jetbrains.kotlin");
  }

  @VisibleForTesting(visibility = Visibility.PRIVATE)
  @NonNull
  protected String getKotlinPluginVersionDescription() {
    try {
      IdeaPluginDescriptor[] pluginDescriptors = PluginManagerCore.getPlugins();
      return Arrays.stream(pluginDescriptors)
                   .filter(d -> d.getPluginId() != null &&
                                d.getPluginId().getIdString().equals("org.jetbrains.kotlin") &&
                                d.isEnabled())
                   .findFirst()
                   .map(d -> d.getVersion())
                   .orElse("pluginNotLoaded");
    }
    catch (Exception ignored) {
      return "exceptionWhenReadingVersion";
    }
  }

  public static class Builder extends BaseBuilder<StudioExceptionReport, Builder> {
    private String exceptionInfo;
    private PluginId pluginId;

    @Override
    protected Builder getThis() {
      return this;
    }

    @NonNull
    public Builder setThrowable(@NonNull Throwable throwable) {
      Throwable cause = getRootCause(throwable);
      this.exceptionInfo = getDescription(cause);
      this.pluginId = IdeErrorsDialog.findPluginId(cause);
      return this;
    }

    @Override
    public StudioExceptionReport build() {
      return new StudioExceptionReport(getVersion(), exceptionInfo, pluginId, getProductData());
    }
  }

  // Similar to ExceptionUtil.getRootCause, but attempts to avoid infinite recursion
  @NonNull
  public static Throwable getRootCause(@NonNull Throwable t) {
    int depth = 0;
    while (depth++ < 20) {
      if (t.getCause() == null) return t;
      t = t.getCause();
    }
    return t;
  }

  /**
   * Returns an exception description (similar to {@link Throwables#getStackTraceAsString(Throwable)}} with the exception message
   * removed in order to strip off any PII. The exception message is include for some specific exceptions where we know that the
   * message will not have any PII.
   */
  @NonNull
  public static String getDescription(@NonNull Throwable t) {
    if (THROWABLE_CLASSES_TO_TRACK_MESSAGES.stream().anyMatch(c -> c.isInstance(t))) {
      return Throwables.getStackTraceAsString(t);
    }

    StringBuilder sb = new StringBuilder(256);

    sb.append(t.getClass().getName());
    sb.append(": <elided>\n"); // note: some message is needed for the backend to parse the report properly

    for (StackTraceElement el : t.getStackTrace()) {
      sb.append("\tat ");
      sb.append(el);
      sb.append('\n');
    }

    return sb.toString();
  }
}
