package ai.markr.phoneagent.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.markr.phoneagent.ui.history.HistoryScreen
import ai.markr.phoneagent.ui.home.HomeScreen
import ai.markr.phoneagent.ui.onboarding.OnboardingScreen
import ai.markr.phoneagent.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenOnboarding = { nav.navigate(Routes.ONBOARDING) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
