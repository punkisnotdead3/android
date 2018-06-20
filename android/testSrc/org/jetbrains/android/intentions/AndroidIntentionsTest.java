package org.jetbrains.android.intentions;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidNonConstantResIdsInSwitchInspection;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidIntentionsTest extends AndroidTestCase {
  private static final String BASE_PATH = "intentions/";

  public void setUpRClass() {
    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      myFixture.copyFileToProject(BASE_PATH + "R.java", "src/p1/p2/R.java");
    }
  }

  public void testSwitchOnResourceId() {
    myFacet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);
    setUpRClass();
    final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
    doTest(inspection, true, inspection.getQuickFixName());
  }

  public void testSwitchOnResourceId1() {
    myFacet.getConfiguration().setProjectType(PROJECT_TYPE_APP);
    setUpRClass();
    final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
    doTest(inspection, false, inspection.getQuickFixName());
  }

  public void testSwitchOnResourceId2() {
    myFacet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);
    setUpRClass();
    final AndroidNonConstantResIdsInSwitchInspection inspection = new AndroidNonConstantResIdsInSwitchInspection();
    doTest(inspection, false, inspection.getQuickFixName());
  }

  private void doTest(final LocalInspectionTool inspection, boolean available, String quickFixName) {
    myFixture.enableInspections(inspection);

    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Class.java");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, false);

    final IntentionAction quickFix = myFixture.getAvailableIntention(quickFixName);
    if (available) {
      assertNotNull(quickFix);
      myFixture.launchAction(quickFix);
      myFixture.checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
    else {
      assertNull(quickFix);
    }
  }
}
