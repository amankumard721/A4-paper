package com.a4lekhak.documentmaker

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.a4lekhak.documentmaker.domain.Templates
import com.a4lekhak.documentmaker.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android/ICU; desktop java.util.regex did not expose the original crash. */
@RunWith(AndroidJUnit4::class)
class TemplateCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun everyTemplateCardOpensPreviewAndCreatesDocument() {
        var index by mutableIntStateOf(0)
        var preview by mutableStateOf(false)
        var created = 0
        compose.setContent {
            LekhakTheme("light", "en") {
                val template = Templates.all[index]
                if (preview)
                    TemplatePreview(template, onBack = { preview = false }) { selected, values ->
                        assertTrue(selected.fields.isNotEmpty())
                        assertTrue(selected.content(values).contains("paragraph"))
                        created++
                        preview = false
                    }
                else TemplateCard(template, onClick = { preview = true })
            }
        }
        Templates.all.forEachIndexed { i, template ->
            compose.runOnIdle { index = i }
            compose.onNodeWithText(template.name).performClick()
            compose.onNodeWithText("Use this template").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(i + 1, created) }
        }
    }
}
