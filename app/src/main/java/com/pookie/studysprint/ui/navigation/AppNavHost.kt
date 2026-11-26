package com.pookie.studysprint.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pookie.studysprint.ui.analytics.AnalyticsScreen
import com.pookie.studysprint.ui.history.HistoryScreen
import com.pookie.studysprint.ui.home.HomeScreen
import com.pookie.studysprint.ui.session.ActiveSessionScreen
import com.pookie.studysprint.ui.session.CreateSessionScreen
import com.pookie.studysprint.ui.settings.SettingsScreen
import com.pookie.studysprint.ui.splash.SplashScreen
import com.pookie.studysprint.ui.subjects.SubjectsScreen
import com.pookie.studysprint.ui.summary.SummaryScreen
import com.pookie.studysprint.ui.tasks.TasksScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val SUBJECTS = "subjects"
    const val CREATE_SESSION = "create_session"
    const val ACTIVE_SESSION = "active_session"

    const val ARG_SUBJECT_ID = "subjectId"
    const val TASKS = "tasks/{$ARG_SUBJECT_ID}"

    const val ARG_SESSION_ID = "sessionId"
    const val SUMMARY = "summary/{$ARG_SESSION_ID}"

    fun tasks(subjectId: Long) = "tasks/$subjectId"
    fun summary(sessionId: Long) = "summary/$sessionId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            // Splash engine ka recovery bhi intezaar karta hai. Adhoora session mila ho
            // to Home ke upar seedha Active screen kholte hain (back = Home).
            SplashScreen(
                onFinished = { hasLiveSession ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                    if (hasLiveSession) {
                        navController.navigate(Routes.ACTIVE_SESSION)
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onPlanSession = { navController.navigate(Routes.CREATE_SESSION) },
                onOpenSubjects = { navController.navigate(Routes.SUBJECTS) },
                onOpenActive = { navController.navigate(Routes.ACTIVE_SESSION) },
                onOpenSummary = { sessionId -> navController.navigate(Routes.summary(sessionId)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANALYTICS) {
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSummary = { sessionId -> navController.navigate(Routes.summary(sessionId)) }
            )
        }
        composable(Routes.SUBJECTS) {
            SubjectsScreen(
                onOpenSubject = { subjectId ->
                    navController.navigate(Routes.tasks(subjectId))
                },
                onPlanSession = {
                    navController.navigate(Routes.CREATE_SESSION)
                },
                onOpenActive = {
                    navController.navigate(Routes.ACTIVE_SESSION)
                }
            )
        }
        composable(
            route = Routes.TASKS,
            arguments = listOf(
                navArgument(Routes.ARG_SUBJECT_ID) { type = NavType.LongType }
            )
        ) {
            TasksScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CREATE_SESSION) {
            CreateSessionScreen(
                onBack = { navController.popBackStack() },
                onSessionStarted = {
                    // Create screen back stack se hata do: Active se back = Subjects.
                    navController.navigate(Routes.ACTIVE_SESSION) {
                        popUpTo(Routes.CREATE_SESSION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ACTIVE_SESSION) {
            ActiveSessionScreen(
                onClosed = {
                    // Summary par ja chuke hain to ye purana signal hai, ignore karo.
                    if (navController.currentDestination?.route == Routes.ACTIVE_SESSION) {
                        navController.popBackStack()
                    }
                },
                onFinished = { sessionId ->
                    navController.navigate(Routes.summary(sessionId)) {
                        popUpTo(Routes.ACTIVE_SESSION) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(
                navArgument(Routes.ARG_SESSION_ID) { type = NavType.LongType }
            )
        ) {
            SummaryScreen(onClose = { navController.popBackStack() })
        }
    }
}