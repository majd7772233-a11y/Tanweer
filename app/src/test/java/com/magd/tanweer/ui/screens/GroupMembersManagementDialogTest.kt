package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.magd.tanweer.data.model.GroupItem
import com.magd.tanweer.data.model.GroupMemberItem
import com.magd.tanweer.data.model.GroupMembersResponse
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class GroupMembersManagementDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollCueRevealsTheLastMemberAndItsActionsWithoutInvokingMembershipMutations() {
        val mutationCallbacks = mutableIntStateOf(0)
        val members = (1..8).map { index ->
            GroupMemberItem(
                userId = "user-$index",
                fullName = "Member $index",
                memberRole = "STUDENT",
                status = "ACTIVE",
                gradeId = 10,
                sectionId = "A"
            )
        }

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    GroupMembersManagementDialog(
                        group = GroupItem(id = "group-1", name = "الشعبة التجريبية", type = "CLASS"),
                        membersResponse = GroupMembersResponse(
                            success = true,
                            canManageMembers = true,
                            members = members
                        ),
                        isLoading = false,
                        onDismiss = {},
                        onUpdateRole = { _, _ -> mutationCallbacks.intValue++ },
                        onRemove = { _, _ -> mutationCallbacks.intValue++ },
                        onApproveJoin = { mutationCallbacks.intValue++ },
                        onRejectJoin = { mutationCallbacks.intValue++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("group_members_scroll_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("group_members_list").performScrollToIndex(8)
        composeRule.onNodeWithTag("group_member_name_user-8").assertIsDisplayed()
        composeRule.onNodeWithTag("group_member_actions_user-8").assertIsDisplayed()
        composeRule.onNodeWithTag("group_member_promote_user-8").assertIsDisplayed()
        composeRule.onNodeWithTag("group_member_remove_user-8").assertIsDisplayed()
        composeRule.onNodeWithTag("group_member_ban_user-8").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals("Scrolling and inspecting must not alter real memberships", 0, mutationCallbacks.intValue)
        }
    }
}
