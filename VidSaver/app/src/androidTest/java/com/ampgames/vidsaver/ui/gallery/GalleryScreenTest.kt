package com.ampgames.vidsaver.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ampgames.vidsaver.MainActivity
import com.ampgames.vidsaver.ui.navigation.VidSaverDestination
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class GalleryScreenTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.onNodeWithTag("tab_${VidSaverDestination.GALLERY.route}").performClick()
    }

    @Test
    fun galleryIsReachableFromTheBottomBar() {
        composeRule.onNodeWithTag(GALLERY_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    /**
     * A fresh install has saved nothing, so the empty state is what should show.
     * The grid case needs real files in MediaStore, which belongs in a device
     * test with fixtures rather than here.
     */
    @Test
    fun anEmptyLibraryShowsTheEmptyState() {
        composeRule.onNodeWithTag(GALLERY_EMPTY_TEST_TAG).assertIsDisplayed()
    }
}
