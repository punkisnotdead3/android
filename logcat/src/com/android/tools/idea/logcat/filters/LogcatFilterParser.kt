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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.Log.LogLevel
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.AND
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.OR
import com.android.tools.idea.logcat.filters.parser.LogcatFilterAndExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLiteralExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterOrExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterParenExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.PROJECT_APP
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.android.tools.idea.logcat.filters.parser.isTopLevelValue
import com.android.tools.idea.logcat.filters.parser.toText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import java.text.ParseException
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DURATION_RE = "\\d+[smhd]".toRegex()

/**
 * Parses a Logcat Filter expression into a [LogcatFilter]
 */
internal class LogcatFilterParser(
  project: Project,
  private val packageNamesProvider: PackageNamesProvider,
  private val joinConsecutiveTopLevelValue: Boolean = false,
  private val topLevelSameKeyTreatment: CombineWith = OR,
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  enum class CombineWith {
    AND,
    OR,
  }

  private val psiFileFactory = PsiFileFactory.getInstance(project)

  fun parse(string: String): LogcatFilter? {
    return try {
      val psi = psiFileFactory.createFileFromText("temp.lcf", LogcatFilterFileType, string)
      if (PsiTreeUtil.hasErrorElements(psi)) {
        val errorElement = PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java) as PsiErrorElement
        throw LogcatFilterParseException(errorElement)
      }
      psi.toFilter()
    }
    catch (e: LogcatFilterParseException) {
      // Any error in parsing results in a filter that matches the raw string with the entire line.
      StringFilter(string, LINE)
    }
  }

  private fun PsiFile.toFilter(): LogcatFilter? {
    val expressions = PsiTreeUtil.getChildrenOfType(this, LogcatFilterExpression::class.java)

    return when {
      expressions == null -> null
      expressions.size == 1 -> expressions[0].toFilter()
      else -> {
        createTopLevelFilter(expressions)
      }
    }
  }

  private fun createTopLevelFilter(expressions: Array<LogcatFilterExpression>): LogcatFilter {
    val filters = if (joinConsecutiveTopLevelValue) combineConsecutiveValues(expressions) else expressions.map { it.toFilter() }

    return when {
      filters.size == 1 -> filters[0]
      topLevelSameKeyTreatment == AND -> AndLogcatFilter(filters)
      else -> createComplexTopLevelFilter(filters)
    }
  }

  private fun createComplexTopLevelFilter(filters: List<LogcatFilter>): LogcatFilter {
    val groups = filters.withIndex().groupBy({ it.value.getFieldForImplicitOr(it.index) }, { it.value }).values
    return AndLogcatFilter(groups.map { if (it.size == 1) it[0] else OrLogcatFilter(it.toList()) })
  }

  private fun combineConsecutiveValues(expressions: Array<LogcatFilterExpression>): List<LogcatFilter> {
    // treat consecutive top level values as concatenations rather than an 'and'.
    // First, group consecutive top level value expressions.
    val grouped = expressions.fold(mutableListOf<MutableList<LogcatFilterExpression>>()) { accumulator, expression ->
      if (expression.isTopLevelValue() && accumulator.isNotEmpty() && accumulator.last().last().isTopLevelValue()) {
        accumulator.last().add(expression)
      }
      else {
        accumulator.add(mutableListOf(expression))
      }
      accumulator
    }

    // Then, combine in an AndFilter while creating a single top level filter for consecutive top-level expressions.
    return grouped.map { if (it.size == 1) it[0].toFilter() else combineLiterals(it) }
  }

  private fun combineLiterals(expressions: List<LogcatFilterExpression>): StringFilter {
    val text = expressions.joinToString("") {
      val expression = it as LogcatFilterLiteralExpression
      expression.firstChild.toText() + if (expression.nextSibling is PsiWhiteSpace) expression.nextSibling.text else ""
    }
    return StringFilter(text.trim(), IMPLICIT_LINE)
  }

  private fun LogcatFilterExpression.toFilter(): LogcatFilter {
    return when (this) {
      is LogcatFilterLiteralExpression -> this.literalToFilter()
      is LogcatFilterParenExpression -> expression!!.toFilter()
      is LogcatFilterAndExpression -> AndLogcatFilter(flattenAndExpression(this).map { it.toFilter() })
      is LogcatFilterOrExpression -> OrLogcatFilter(flattenOrExpression(this).map { it.toFilter() })
      else -> throw ParseException("Unexpected element: ${this::class.simpleName}", -1) // Should not happen
    }
  }

  private fun LogcatFilterLiteralExpression.literalToFilter() =
    when (firstChild.elementType) {
      VALUE -> StringFilter(firstChild.toText(), IMPLICIT_LINE)
      KEY, STRING_KEY, REGEX_KEY -> toKeyFilter(clock)
      PROJECT_APP -> ProjectAppFilter(packageNamesProvider)
      else -> throw ParseException("Unexpected elementType: $firstChild.elementType", -1) // Should not happen
    }
}

private fun LogcatFilterLiteralExpression.toKeyFilter(clock: Clock): LogcatFilter {
  return when (val key = firstChild.text.trim(':', '-', '~')) {
    "level" -> LevelFilter(lastChild.asLogLevel())
    "fromLevel" -> FromLevelFilter(lastChild.asLogLevel())
    "toLevel" -> ToLevelFilter(lastChild.asLogLevel())
    "age" -> AgeFilter(lastChild.asDuration(), clock)
    else -> {
      val value = lastChild.toText()
      val isNegated = firstChild.text.startsWith('-')
      val isRegex = firstChild.text.endsWith("~:")
      val field: LogcatFilterField =
        when (key) {
          "tag" -> TAG
          "package" -> APP
          "message" -> MESSAGE
          "line" -> LINE
          else -> {
            throw LogcatFilterParseException(PsiErrorElementImpl("Invalid key: $key")) // Should not happen
          }
        }

      when {
        isNegated && isRegex -> NegatedRegexFilter(value, field)
        isNegated -> NegatedStringFilter(value, field)
        isRegex -> RegexFilter(value, field)
        else -> StringFilter(value, field)
      }
    }
  }
}

private fun PsiElement.asLogLevel(): LogLevel =
  if (text.length == 1) LogLevel.getByLetter(text[0].toUpperCase())
  else LogLevel.getByString(text.toLowerCase(Locale.ROOT))
       ?: throw LogcatFilterParseException(PsiErrorElementImpl("Invalid Log Level: $text"))

private fun PsiElement.asDuration(): Duration {
  DURATION_RE.matchEntire(text) ?: throw LogcatFilterParseException(PsiErrorElementImpl("Invalid duration: $text"))
  val count = try {
    text.substring(0, text.length - 1).toLong()
  }
  catch (e: NumberFormatException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid duration: $text"))
  }
  val l = when (text.last()) {
    's' -> count
    'm' -> TimeUnit.MINUTES.toSeconds(count)
    'h' -> TimeUnit.HOURS.toSeconds(count)
    'd' -> TimeUnit.DAYS.toSeconds(count)
    else -> throw LogcatFilterParseException(PsiErrorElementImpl("Invalid duration: $text")) // should not happen
  }
  return Duration.ofSeconds(l)
}

private fun flattenOrExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterOrExpression) {
    flattenOrExpression(expression.expressionList[0]) + flattenOrExpression(expression.expressionList[1])
  }
  else {
    listOf(expression)
  }

private fun flattenAndExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterAndExpression) {
    flattenAndExpression(expression.expressionList[0]) + flattenAndExpression(expression.expressionList[1])
  }
  else {
    listOf(expression)
  }

private data class FilterType(private val obj: Any)

/**
 * Returns a [FilterType] to be used for grouping.
 *
 * The grouping determines if they will be combined with an 'OR' or an 'AND'. The filters inside each group will be combined with an 'OR'
 * while the groups will be combined with an 'AND'.
 *
 * Grouping rules:
 *   1. StringFilter & RegexFilter are grouped by their `field`.
 *   2. All level filters are grouped together.
 *   3. AgeFilter's are grouped together.
 *   2. Any other filter (including NegatedStringFilter & NegatedRegexFilter) are not grouped and get a unique FilterType which is
 *   established using its index.
 */
private fun LogcatFilter.getFieldForImplicitOr(index: Int): FilterType {
  return when {
    this is StringFilter && field != IMPLICIT_LINE -> FilterType(field)
    this is RegexFilter -> FilterType(field)
    this is LevelFilter || this is FromLevelFilter || this is ToLevelFilter -> FilterType("level")
    this is AgeFilter -> FilterType("age")
    else -> FilterType(index)
  }
}
