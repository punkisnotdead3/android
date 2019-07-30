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
package com.android.tools.idea.ui.resourcemanager

import com.android.resources.ResourceType
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.explorer.AssetListView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceDetailView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.google.common.truth.Truth.assertThat
import com.intellij.application.runInAllowSaveMode
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.test.assertNull

private const val WAIT_TIMEOUT = 3000

class ResourceExplorerDialogTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var pickerDialog: ResourceExplorerDialog

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    projectRule.fixture.copyDirectoryToProject("res/", "res/")
    pickerDialog = createResourcePickerDialog(false)
    Disposer.register(projectRule.project, pickerDialog.disposable)
  }

  @Test
  fun updateSelectedResource() {
    // Save project to guarantee project.getProjectFile() is non-null.
    runInEdtAndWait { runInAllowSaveMode { projectRule.project.save() } }
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.ui = HeadlessListUI()

    var point = list.indexToLocation(0)
    simulateMouseClick(list, point, 1)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")

    point = list.indexToLocation(1)
    simulateMouseClick(list, point, 1)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/vector_drawable")
  }

  @Test
  fun selectResource() {
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.ui = HeadlessListUI()
    val point = list.indexToLocation(0)
    // Simulate double clicking on an asset.
    simulateMouseClick(list, point, 2)
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")
  }

  @Test
  fun selectSampleDataResource() {
    pickerDialog = createResourcePickerDialog(true)
    Disposer.register(projectRule.project, pickerDialog.disposable)
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!

    var sampleDataList: AssetListView? = null
    val waitForSampleDataList = object : WaitFor(WAIT_TIMEOUT) {
      public override fun condition(): Boolean {
        val listViews = UIUtil.findComponentsOfType(explorerView, AssetListView::class.java)
        listViews.forEach { listView ->
          if (listView.model.getElementAt(0).assets.first().resourceItem.type == ResourceType.SAMPLE_DATA) {
            // Make sure there are actually sample data resources being displayed.
            sampleDataList = listView
            return true
          }
        }
        return false
      }
    }
    assertThat(waitForSampleDataList.isConditionRealized).isTrue()

    sampleDataList!!.ui = HeadlessListUI()
    val point = sampleDataList!!.indexToLocation(0)
    simulateMouseClick(sampleDataList!!, point, 2)
    // We don't know for a fact what resource will come first, so just check that the format is correct.
    assertThat(pickerDialog.resourceName).startsWith("@tools:sample/")
  }

  @Test
  fun selectMultipleConfigurationResource() {
    val resDir = projectRule.fixture.copyDirectoryToProject("res/", "res/")
    runInEdtAndWait {
      runWriteAction {
        // Add a second configuration to the "png.png" resource.
        val hdpiDir = resDir.createChildDirectory(this, "drawable-hdpi")
        resDir.findFileByRelativePath("drawable/png.png")!!.copy(this, hdpiDir, "png.png")
      }
    }
    setUp()
    val explorerView = UIUtil.findComponentOfType(pickerDialog.resourceExplorerPanel, ResourceExplorerView::class.java)!!
    val list = UIUtil.findComponentOfType(explorerView, AssetListView::class.java)!!
    list.ui = HeadlessListUI()
    val point = list.indexToLocation(0)
    // First resource should now have 2 versions.
    assertThat(list.model.getElementAt(0).assets).hasSize(2)
    // Simulate double clicking on the first resource.
    simulateMouseClick(list, point, 2)
    // Should properly select the resource (instead of showing the detailed view).
    assertThat(pickerDialog.resourceName).isEqualTo("@drawable/png")
    assertNull(UIUtil.findComponentOfType(explorerView, ResourceDetailView::class.java))
  }

  private fun createResourcePickerDialog(showSampleData: Boolean): ResourceExplorerDialog {
    var explorerDialog: ResourceExplorerDialog? = null
    runInEdtAndWait {
      explorerDialog = ResourceExplorerDialog(facet = AndroidFacet.getInstance(projectRule.module)!!,
                                              initialResourceUrl = null,
                                              supportedTypes = setOf(ResourceType.DRAWABLE),
                                              showSampleData = showSampleData,
                                              currentFile = null)
    }
    assertThat(explorerDialog).isNotNull()
    explorerDialog?.let { view ->
      val explorerView = UIUtil.findComponentOfType(view.resourceExplorerPanel, ResourceExplorerView::class.java)!!
      waitAndAssertListView(explorerView) { it != null }
    }
    return explorerDialog!!
  }

  private fun simulateMouseClick(component: JComponent, point: Point, clickCount: Int) {
    runInEdtAndWait {
      // A click is done through a mouse pressed & released event, followed by the actual mouse clicked event.
      component.dispatchEvent(MouseEvent(
        component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
      component.dispatchEvent(MouseEvent(
        component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
      component.dispatchEvent(MouseEvent(
        component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, clickCount, false))
    }
  }
}

private fun waitAndAssertListView(view: ResourceExplorerView, condition: (list: AssetListView?) -> Boolean) {
  val waitForAssetListView = object : WaitFor(WAIT_TIMEOUT) {
    public override fun condition() = condition(UIUtil.findComponentOfType(view, AssetListView::class.java))
  }
  assertThat(waitForAssetListView.isConditionRealized).isTrue()
}