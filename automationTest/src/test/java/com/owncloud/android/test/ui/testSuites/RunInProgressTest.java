package org.elastos.ditto.test.ui.testSuites;

import org.junit.experimental.categories.Categories.IncludeCategory;
import org.junit.runner.RunWith;
import org.elastos.ditto.test.ui.groups.FlexibleCategories;
import org.elastos.ditto.test.ui.groups.InProgressCategory;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestClassPrefix;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestClassSuffix;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestScanPackage;


@RunWith(FlexibleCategories.class)
@IncludeCategory(InProgressCategory.class)
@TestScanPackage("com.owncloud.android.test.ui.testSuites")
@TestClassPrefix("")
@TestClassSuffix("TestSuite")
public class RunInProgressTest {

}
