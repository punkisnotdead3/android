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
package com.android.tools.adtui.common

/**
 * Represents a y position in swing space
 * Corresponds to the [SwingCoordinate] attribute
 */
inline class SwingY(val value: Float) {
  operator fun plus(rhs: SwingLength) = SwingY(value + rhs.value)
  operator fun minus(rhs: SwingLength) = SwingY(value - rhs.value)
  operator fun minus(rhs: SwingY) = SwingLength(value - rhs.value)
  operator fun compareTo(rhs: SwingY) = value.compareTo(rhs.value)
  fun toInt() = value.toInt()
  fun toDouble() = value.toDouble()
  override fun toString() = value.toString()
}

fun String.toSwingY() = SwingY(this.toFloat())
fun interpolate(start: SwingY, end: SwingY, fraction: Float) = start + (end - start) * fraction