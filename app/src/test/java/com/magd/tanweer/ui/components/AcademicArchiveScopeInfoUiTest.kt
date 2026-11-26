package com.magd.tanweer.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.R
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicArchiveScopeInfoUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arabicPersonalViewExplainsAccountWideScopeInRtl() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val arabicConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val arabicContext = baseContext.createConfigurationContext(arabicConfiguration)

        composeRule.setContent {
            TanweerTheme {
                CompositionLocalProvider(
                    LocalContext provides arabicContext,
                    LocalConfiguration provides arabicConfiguration,
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    AcademicArchiveScopeInfo(isPersonalView = true, modifier = Modifier.width(320.dp))
                }
            }
        }

        composeRule.onNodeWithText(arabicContext.getString(R.string.academic_archive_personal_scope_title)).assertIsDisplayed()
        composeRule.onNodeWithText(arabicContext.getString(R.string.academic_archive_personal_scope_description)).assertIsDisplayed()
    }

    @Test
    fun arabicGroupViewExplainsSelectedGroupScopeInRtl() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val arabicConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val arabicContext = baseContext.createConfigurationContext(arabicConfiguration)

        composeRule.setContent {
            TanweerTheme {
                CompositionLocalProvider(
                    LocalContext provides arabicContext,
                    LocalConfiguration provides arabicConfiguration,
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    AcademicArchiveScopeInfo(isPersonalView = false, modifier = Modifier.width(320.dp))
                }
            }
        }

        composeRule.onNodeWithText(arabicContext.getString(R.string.academic_archive_group_scope_title)).assertIsDisplayed()
        composeRule.onNodeWithText(arabicContext.getString(R.string.academic_archive_group_scope_description)).assertIsDisplayed()
    }

    @Test
    fun englishPersonalViewExplainsAccountWideScopeInLtr() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val englishConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.US)
        }
        val englishContext = baseContext.createConfigurationContext(englishConfiguration)

        composeRule.setContent {
            TanweerTheme {
                CompositionLocalProvider(
                    LocalContext provides englishContext,
                    LocalConfiguration provides englishConfiguration,
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    AcademicArchiveScopeInfo(isPersonalView = true, modifier = Modifier.width(320.dp))
                }
            }
        }

        composeRule.onNodeWithText(englishContext.getString(R.string.academic_archive_personal_scope_title)).assertIsDisplayed()
        composeRule.onNodeWithText(englishContext.getString(R.string.academic_archive_personal_scope_description)).assertIsDisplayed()
    }

    @Test
    fun englishGroupViewExplainsSelectedGroupScopeInLtr() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val englishConfiguration = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.US)
        }
        val englishContext = baseContext.createConfigurationContext(englishConfiguration)

        composeRule.setContent {
            TanweerTheme {
                CompositionLocalProvider(
                    LocalContext provides englishContext,
                    LocalConfiguration provides englishConfiguration,
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    AcademicArchiveScopeInfo(isPersonalView = false, modifier = Modifier.width(320.dp))
                }
            }
        }

        composeRule.onNodeWithText(englishContext.getString(R.string.academic_archive_group_scope_title)).assertIsDisplayed()
        composeRule.onNodeWithText(englishContext.getString(R.string.academic_archive_group_scope_description)).assertIsDisplayed()
    }
}