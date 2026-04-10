package com.internshipuncle.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.internshipuncle.core.ui.AppShell
import com.internshipuncle.core.ui.TopLevelDestination
import com.internshipuncle.feature_analyze.AnalysisScreen
import com.internshipuncle.feature_analyze.AnalysisViewModel
import com.internshipuncle.feature_auth.AuthGateViewModel
import com.internshipuncle.feature_auth.LoginScreen
import com.internshipuncle.feature_auth.OnboardingScreen
import com.internshipuncle.feature_auth.SignupScreen
import com.internshipuncle.feature_auth.SplashScreen
import com.internshipuncle.feature_dashboard.DashboardScreen
import com.internshipuncle.feature_dashboard.DashboardViewModel
import com.internshipuncle.feature_interview.MockInterviewScreen
import com.internshipuncle.feature_interview.MockInterviewViewModel
import com.internshipuncle.feature_jobs.JobDetailScreen
import com.internshipuncle.feature_jobs.JobDetailViewModel
import com.internshipuncle.feature_jobs.JobsScreen
import com.internshipuncle.feature_jobs.JobsViewModel
import com.internshipuncle.feature_jobs.SavedJobsScreen
import com.internshipuncle.feature_jobs.SavedJobsViewModel
import com.internshipuncle.feature_resume.ResumeBuilderScreen
import com.internshipuncle.feature_resume.ResumeBuilderViewModel
import com.internshipuncle.feature_resume.ResumeRoastScreen
import com.internshipuncle.feature_resume.ResumeRoastViewModel
import com.internshipuncle.feature_resume.ResumeUploadScreen
import com.internshipuncle.feature_resume.ResumeUploadViewModel

@Composable
fun InternshipUncleApp() {
    val navController = rememberNavController()
    val authGateViewModel: AuthGateViewModel = hiltViewModel()
    val session by authGateViewModel.session.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = backStackEntry?.destination?.route

    val topLevelDestinations = listOf(
        TopLevelDestination("Jobs", AppDestination.Jobs.route),
        TopLevelDestination("Analyze", AppDestination.Analysis.createRoute("android-intern-1")),
        TopLevelDestination("Resume", AppDestination.ResumeUpload.createRoute()),
        TopLevelDestination("Interview", AppDestination.MockInterview.route),
        TopLevelDestination("Dashboard", AppDestination.Dashboard.route)
    )

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        topLevelDestinations.any { routeMatches(destination.route, it.route) }
    } == true

    LaunchedEffect(session, currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        val targetRoute = resolveAuthRoute(
            currentRoute = route,
            session = session
        ) ?: return@LaunchedEffect

        if (targetRoute != route) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    AppShell(
        title = currentRoute.toTitle(),
        showBottomBar = showBottomBar,
        destinations = topLevelDestinations,
        selectedRoute = topLevelDestinations.firstOrNull { routeMatches(currentRoute, it.route) }?.route
            ?: currentRoute,
        onDestinationSelected = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Splash.route) {
                SplashScreen(session = session)
            }
            composable(AppDestination.Login.route) {
                LoginScreen(
                    onSignup = { navController.navigate(AppDestination.Signup.route) }
                )
            }
            composable(AppDestination.Signup.route) {
                SignupScreen(
                    onLogin = { navController.popBackStack() }
                )
            }
            composable(AppDestination.Onboarding.route) {
                OnboardingScreen()
            }
            composable(
                route = AppDestination.ResumeUpload.route,
                arguments = listOf(navArgument("targetJobId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                val viewModel: ResumeUploadViewModel = hiltViewModel()
                ResumeUploadScreen(
                    viewModel = viewModel,
                    onOpenRoast = { resumeId, targetJobId ->
                        navController.navigate(AppDestination.ResumeRoast.createRoute(resumeId, targetJobId))
                    },
                    onOpenBuilder = { targetJobId ->
                        navController.navigate(AppDestination.ResumeBuilder.createRoute(targetJobId))
                    }
                )
            }
            composable(
                route = AppDestination.ResumeBuilder.route,
                arguments = listOf(navArgument("targetJobId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                val viewModel: ResumeBuilderViewModel = hiltViewModel()
                ResumeBuilderScreen(viewModel = viewModel)
            }
            composable(AppDestination.Jobs.route) {
                val viewModel: JobsViewModel = hiltViewModel()
                JobsScreen(
                    viewModel = viewModel,
                    onOpenJob = { jobId -> navController.navigate(AppDestination.JobDetail.createRoute(jobId)) },
                    onOpenSavedJobs = { navController.navigate(AppDestination.SavedJobs.route) }
                )
            }
            composable(AppDestination.SavedJobs.route) {
                val viewModel: SavedJobsViewModel = hiltViewModel()
                SavedJobsScreen(
                    viewModel = viewModel,
                    onOpenJob = { jobId -> navController.navigate(AppDestination.JobDetail.createRoute(jobId)) },
                    onBrowseJobs = { navController.popBackStack() }
                )
            }
            composable(
                route = AppDestination.JobDetail.route,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                val viewModel: JobDetailViewModel = hiltViewModel()
                JobDetailScreen(
                    viewModel = viewModel,
                    onOpenAnalysis = { jobId -> navController.navigate(AppDestination.Analysis.createRoute(jobId)) },
                    onOpenResume = { jobId -> navController.navigate(AppDestination.ResumeUpload.createRoute(jobId)) },
                    onOpenInterview = { navController.navigate(AppDestination.MockInterview.route) }
                )
            }
            composable(
                route = AppDestination.Analysis.route,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                val viewModel: AnalysisViewModel = hiltViewModel()
                AnalysisScreen(viewModel = viewModel)
            }
            composable(
                route = AppDestination.ResumeRoast.route,
                arguments = listOf(
                    navArgument("resumeId") { type = NavType.StringType },
                    navArgument("targetJobId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                val viewModel: ResumeRoastViewModel = hiltViewModel()
                ResumeRoastScreen(viewModel = viewModel)
            }
            composable(AppDestination.MockInterview.route) {
                val viewModel: MockInterviewViewModel = hiltViewModel()
                MockInterviewScreen(viewModel = viewModel)
            }
            composable(AppDestination.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}

private fun resolveAuthRoute(
    currentRoute: String,
    session: com.internshipuncle.data.repository.AuthSession
): String? {
    return when {
        session.isRestoring -> if (currentRoute == AppDestination.Splash.route) null else AppDestination.Splash.route
        !session.isLoggedIn -> when (currentRoute) {
            AppDestination.Login.route,
            AppDestination.Signup.route -> null

            else -> AppDestination.Login.route
        }

        session.needsOnboarding -> if (currentRoute == AppDestination.Onboarding.route) {
            null
        } else {
            AppDestination.Onboarding.route
        }

        currentRoute == AppDestination.Splash.route ||
            currentRoute == AppDestination.Login.route ||
            currentRoute == AppDestination.Signup.route ||
            currentRoute == AppDestination.Onboarding.route -> AppDestination.Jobs.route

        else -> null
    }
}

private fun String?.toTitle(): String {
    return when (this) {
        AppDestination.Splash.route -> "Getting the workflow ready"
        AppDestination.Login.route -> "Sign in to your prep flow"
        AppDestination.Signup.route -> "Create your account"
        AppDestination.Onboarding.route -> "Pick your target roles"
        AppDestination.Jobs.route -> "Discover curated internships"
        AppDestination.SavedJobs.route -> "Saved internships"
        AppDestination.ResumeUpload.route -> "Resume Lab"
        AppDestination.ResumeBuilder.route -> "Resume Builder"
        AppDestination.MockInterview.route -> "Interview Prep"
        AppDestination.Dashboard.route -> "Readiness dashboard"
        else -> when {
            this?.startsWith("resume/upload") == true -> "Resume Lab"
            this?.startsWith("resume/builder") == true -> "Resume Builder"
            this?.startsWith("resume/roast/") == true -> "Resume roast"
            this?.startsWith("job/") == true -> "Target role overview"
            this?.startsWith("analysis/") == true -> "JD reality check"
            else -> "Internship prep"
        }
    }
}

private fun routeMatches(currentRoute: String?, destinationRoute: String): Boolean {
    if (currentRoute.isNullOrBlank()) return false
    return routeBase(currentRoute) == routeBase(destinationRoute)
}

private fun routeBase(route: String): String {
    return route.substringBefore("?").substringBefore("/{")
}
