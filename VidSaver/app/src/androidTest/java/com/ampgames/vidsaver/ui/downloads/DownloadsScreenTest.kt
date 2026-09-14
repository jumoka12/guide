package com.ampgames.vidsaver.ui.downloads

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
class DownloadsScreenTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.onNodeWithTag("tab_${VidSaverDestination.DOWNLOADS.route}").performClick()
    }

    @Test
    fun anEmptyQueueShowsTheEmptyState() {
        composeRule.onNodeWithTag(DOWNLOADS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DOWNLOADS_EMPTY_TEST_TAG).assertIsDisplayed()
    }
}
