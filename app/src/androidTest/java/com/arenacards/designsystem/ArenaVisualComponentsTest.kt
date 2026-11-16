package com.arenacards.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ArenaVisualComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun yellowCardIsAccessibleAndActivatesItsAction() {
        composeRule.setContent {
            ArenaCardsTheme {
                val clickCount = remember { mutableIntStateOf(0) }
                ArenaCardTile(
                    color = ArenaCardColor.YELLOW,
                    value = 5,
                    selected = true,
                    onClick = { clickCount.intValue++ }
                )
                androidx.compose.material3.Text("Actions: ${clickCount.intValue}")
            }
        }

        composeRule.onNodeWithContentDescription("5 yellow card, playable, selected")
            .assertExists()
            .performClick()
        composeRule.onNodeWithText("Actions: 1").assertExists()
    }

    @Test
    fun disabledCardIsNotExposedAsEnabled() {
        composeRule.setContent {
            ArenaCardsTheme {
                ArenaCardTile(
                    color = ArenaCardColor.BLUE,
                    value = 12,
                    enabled = false,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Draw Two blue card")
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun draggingCardToDropTargetActivatesItsAction() {
        composeRule.setContent {
            ArenaCardsTheme {
                val dropBounds = remember { mutableStateOf<Rect?>(null) }
                val dropCount = remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .size(100.dp)
                            .testTag("discard-target")
                            .onGloballyPositioned { dropBounds.value = it.boundsInRoot() }
                    )
                    ArenaCardTile(
                        color = ArenaCardColor.GREEN,
                        value = 7,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        dropTargetBounds = dropBounds.value,
                        onDrop = { dropCount.intValue++ }
                    )
                    androidx.compose.material3.Text("Drops: ${dropCount.intValue}")
                }
            }
        }

        composeRule.waitForIdle()
        val targetCenter = composeRule.onNodeWithTag("discard-target")
            .fetchSemanticsNode().boundsInRoot.center
        val cardCenter = composeRule.onNodeWithContentDescription("7 green card")
            .fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithContentDescription("7 green card").performTouchInput {
            down(center)
            moveBy(Offset(0f, targetCenter.y - cardCenter.y))
            up()
        }

        composeRule.onNodeWithText("Drops: 1").assertExists()
    }

    @Test
    fun premiumCardExposesItsOriginalCardIdentity() {
        composeRule.setContent {
            ArenaCardsTheme {
                ArenaPremiumCard(
                    color = ArenaCardColor.WILD,
                    value = 14,
                    selected = true,
                    reducedMotion = true
                )
            }
        }

        composeRule.onNodeWithContentDescription("Wild Draw Four wild ArenaCards card")
            .assertExists()
    }

    @Test
    fun premiumCardBackExposesBrandIdentity() {
        composeRule.setContent {
            ArenaCardsTheme {
                ArenaCardBack()
            }
        }

        composeRule.onNodeWithContentDescription("ArenaCards premium card back")
            .assertExists()
    }

    @Test
    fun avatarKeepsPlayerIdentityAccessible() {
        composeRule.setContent {
            ArenaCardsTheme {
                Column {
                    PlayerAvatar(
                        initials = "",
                        name = "Arena player",
                        size = 48.dp,
                        avatarId = "comet"
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Arena player avatar").assertExists()
    }
}
