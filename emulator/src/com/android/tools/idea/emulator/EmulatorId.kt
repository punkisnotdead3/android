/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

/**
 * Identifying information for a running Emulator.
 */
// TODO: Don't accept null avdDir and empty commandLine once b/148935382 is fixed.
data class EmulatorId(val grpcPort: Int, val grpcCertificate: String, val avdId: String, val avdName: String, val avdDir: String?,
                      val serialPort: Int, val adbPort: Int, val commandLine: List<String>, val registrationFileName: String) {
  override fun toString(): String {
    return "$avdId @ $grpcPort"
  }

  val isEmbedded: Boolean
    get() = commandLine.isEmpty() || commandLine.contains("auto-no-window")
}