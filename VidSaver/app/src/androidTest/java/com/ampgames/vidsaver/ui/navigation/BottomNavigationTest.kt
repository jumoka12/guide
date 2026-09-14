package com.ampgames.vidsaver.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ampgames.vidsaver.MainActivity
import com.ampgames.vidsaver.ui.browser.BROWSER_SCREEN_TEST_TAG
import com.ampgames.vidsaver.ui.downloads.DOWNLOADS_SCREEN_TEST_TAG
import com.ampgames.vidsaver.ui.gallery.GALLERY_SCREEN_TEST_TAG
import com.ampgames.vidsaver.ui.settings.SETTINGS_SCREEN_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** One assertion per main screen: each tab shows its own screen. */
@HiltAndroidTest
class BottomNavigationTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun browserIsTheStartDestination() {
        composeRule.onNodeWithTag(BROWSER_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_BAR_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun downloadsTabShowsDownloadsScreen() {
        composeRule.onNodeWithTag("tab_downloads").performClick()
        composeRule.onNodeWithTag(DOWNLOADS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun galleryTabShowsGalleryScreen() {
        composeRule.onNodeWithTag("tab_gallery").performClick()
        composeRule.onNodeWithTag(GALLERY_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun settingsTabShowsSettingsScreen() {
        composeRule.onNodeWithTag("tab_settings").performClick()
        composeRule.onNodeWithTag(SETTINGS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun returningToBrowserRestoresIt() {
        composeRule.onNodeWithTag("tab_settings").performClick()
        composeRule.onNodeWithTag("tab_browser").performClick()
        composeRule.onNodeWithTag(BROWSER_SCREEN_TEST_TAG).assertIsDisplayed()
    }
}
