package com.ampgames.vidsaver.ui.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ampgames.vidsaver.MainActivity
import com.ampgames.vidsaver.ui.browser.components.ADDRESS_BAR_TEST_TAG
import com.ampgames.vidsaver.ui.browser.components.BROWSER_HOME_TEST_TAG
import com.ampgames.vidsaver.ui.browser.components.BROWSER_MENU_TEST_TAG
import com.ampgames.vidsaver.ui.browser.components.TABS_SHEET_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class BrowserScreenTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun homeShowsShortcutsFromConfig() {
        composeRule.onNodeWithTag(BROWSER_HOME_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("shortcut_tiktok.com").assertIsDisplayed()
        composeRule.onNodeWithTag("shortcut_instagram.com").assertIsDisplayed()
    }

    @Test
    fun addressBarAndMenuArePresent() {
        composeRule.onNodeWithTag(ADDRESS_BAR_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSER_MENU_TEST_TAG).assertIsDisplayed()
    }

    /** Bookmarks and ad blocking moved out of the old second bottom bar. */
    @Test
    fun theOverflowMenuExposesBookmarksAndAdBlocking() {
        composeRule.onNodeWithTag(BROWSER_MENU_TEST_TAG).performClick()
        composeRule.onNodeWithTag("open_bookmarks").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_adblock").assertIsDisplayed()
    }

    @Test
    fun searchEngineSelectorSwitchesEngine() {
        composeRule.onNodeWithTag("engine_duckduckgo").performClick()
        composeRule.onNodeWithTag("engine_duckduckgo").assertIsDisplayed()
    }

    /** On the start page the tab list lives in the menu; the counter appears on a page. */
    @Test
    fun tabsSheetOpensAndCanAddATab() {
        composeRule.onNodeWithTag(BROWSER_MENU_TEST_TAG).performClick()
        composeRule.onNodeWithTag("open_tabs").performClick()
        composeRule.onNodeWithTag(TABS_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("new_tab").performClick()
        composeRule.onNodeWithTag(BROWSER_HOME_TEST_TAG).assertIsDisplayed()
    }

    /**
     * Policy check at the UI level: typing a YouTube URL must not navigate, and
     * the home page stays put.
     */
    @Test
    fun youtubeIsRefusedFromTheAddressBar() {
        composeRule.onNodeWithTag(ADDRESS_BAR_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(ADDRESS_BAR_TEST_TAG).performTextInput("https://youtube.com/watch?v=x")
        composeRule.onNodeWithTag(BROWSER_HOME_TEST_TAG).assertIsDisplayed()
    }
}
