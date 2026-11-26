package com.magd.tanweer.ui

/** The screen and root tab that opened Groups and should receive the next Back action. */
data class GroupsReturnDestination(
    val subScreen: SubScreen,
    val currentTab: NavigationTab
)

/** Restricts Groups return navigation to the confirmed moderator-dashboard entry flow. */
object GroupsNavigationPolicy {
    fun resolve(
        origin: GroupsReturnDestination?,
        fallbackTab: NavigationTab
    ): GroupsReturnDestination {
        return origin?.takeIf { it.subScreen == SubScreen.MODERATOR_DASHBOARD }
            ?: GroupsReturnDestination(
                subScreen = SubScreen.NONE,
                currentTab = fallbackTab
            )
    }
}
