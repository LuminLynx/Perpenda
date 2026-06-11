package com.perpenda.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.perpenda.data.repository.RepositoryProvider
import com.perpenda.ui.about.AboutScreen
import com.perpenda.ui.auth.AuthScreen
import com.perpenda.ui.components.CenterNotice
import com.perpenda.ui.components.CenterNoticeOverlay
import com.perpenda.ui.library.GlossaryLibraryScreen
import com.perpenda.ui.path.PathHomeScreen
import com.perpenda.ui.preview.TokenizationProofScreen
import com.perpenda.ui.preview.bite.BiteFeedScreen
import com.perpenda.ui.preview.bite.tokenizationBites
import com.perpenda.ui.settings.SettingsScreen
import com.perpenda.ui.unit.UnitReaderScreen
import com.perpenda.viewmodel.AuthMode

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val glossaryRepository = remember { RepositoryProvider.glossaryRepository }
    val authRepository = remember { RepositoryProvider.authRepository }
    val pathRepository = remember { RepositoryProvider.pathRepository }
    val completionCache = remember { RepositoryProvider.completionCacheInstance }
    val themePreferenceStore = remember { RepositoryProvider.themePreferenceStoreInstance }

    // Lives above the NavHost so a notice raised on one screen (e.g. the
    // auth flow's "account ready") stays visible across the navigation
    // that immediately follows it.
    var centerNotice by remember { mutableStateOf<CenterNotice?>(null) }

    Box {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                PathHomeScreen(
                    pathRepository = pathRepository,
                    completionCache = completionCache,
                    onOpenUnit = { unitId -> navController.navigate("unit/$unitId") },
                    onOpenSettings = { navController.navigate("settings") },
                    onAuthExpired = { navController.navigate("auth_login") },
                    onNotice = { centerNotice = CenterNotice.of(it) }
                )
            }
            composable(
                route = "unit/{unitId}",
                arguments = listOf(navArgument("unitId") { type = NavType.StringType })
            ) { backStackEntry ->
                val unitId = backStackEntry.arguments?.getString("unitId").orEmpty()
                UnitReaderScreen(
                    pathRepository = pathRepository,
                    completionCache = completionCache,
                    unitId = unitId,
                    // "Next unit" from the grade results: replace this reader
                    // on the back stack so back goes home, not through a
                    // chain of finished readers.
                    onOpenNextUnit = { nextId ->
                        navController.navigate("unit/$nextId") {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onAuthExpired = {
                        // Pop the unit reader off the back stack on the way to
                        // auth_login. Otherwise hitting back from the sign-in
                        // screen drops the user back onto the unit reader's
                        // lingering Error(authExpired) state, which looks like
                        // a server error to the user. After auth they re-tap
                        // Continue from path home — one extra tap, no confusing
                        // error screen.
                        navController.navigate("auth_login") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }
            composable("glossary") {
                GlossaryLibraryScreen(repository = glossaryRepository)
            }
            composable("about") {
                AboutScreen(
                    authRepository = authRepository,
                    onAccountDeleted = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    authRepository = authRepository,
                    themePreferenceStore = themePreferenceStore,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("auth_login") {
                AuthScreen(
                    initialMode = AuthMode.Login,
                    authRepository = authRepository,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = { navController.popBackStack() },
                    onNotice = { centerNotice = CenterNotice.of(it) }
                )
            }
            composable("auth_signup") {
                AuthScreen(
                    initialMode = AuthMode.Signup,
                    authRepository = authRepository,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = { navController.popBackStack() },
                    onNotice = { centerNotice = CenterNotice.of(it) }
                )
            }
            composable("preview_tokenization") {
                TokenizationProofScreen(onBack = { navController.popBackStack() })
            }
            composable("preview_tokenization_bite") {
                BiteFeedScreen(
                    bites = tokenizationBites(),
                    onClose = { navController.popBackStack() }
                )
            }
        }

        CenterNoticeOverlay(
            notice = centerNotice,
            onDismiss = { centerNotice = null }
        )
    }
}
