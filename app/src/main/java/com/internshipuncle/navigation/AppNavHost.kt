package com.internshipuncle.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import com.internshipuncle.core.ui.PlaceholderScreen
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
import com.internshipuncle.feature_interview.MockInterviewSessionScreen
import com.internshipuncle.feature_interview.MockInterviewSessionViewModel
import com.internshipuncle.feature_interview.MockInterviewSetupScreen
import com.internshipuncle.feature_interview.MockInterviewSetupViewModel
import com.internshipuncle.feature_interview.MockInterviewSummaryScreen
import com.internshipuncle.feature_interview.MockInterviewSummaryViewModel
import com.internshipuncle.feature_jobs.JobDetailScreen
import com.internshipuncle.feature_jobs.JobDetailViewModel
import com.internshipuncle.feature_jobs.JobsScreen
import com.internshipuncle.feature_jobs.JobsViewModel
import com.internshipuncle.feature_jobs.SavedJobsScreen
import com.internshipuncle.feature_jobs.SavedJobsViewModel
import com.internshipuncle.feature_more.CommunityFeedScreen
import com.internshipuncle.feature_more.MoreScreen
import com.internshipuncle.feature_more.ProgressScreen
import com.internshipuncle.feature_more.RealityCheckScreen
import com.internshipuncle.feature_more.ReferralsScreen
import com.internshipuncle.feature_profile.ProfileScreen
import com.internshipuncle.feature_profile.ProfileViewModel
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
        TopLevelDestination("Home",      AppDestination.Dashboard.route,               Icons.Outlined.Home),
        TopLevelDestination("Internships", AppDestination.Jobs.route,                 Icons.Outlined.BusinessCenter),
        TopLevelDestination("Resume",    AppDestination.ResumeUpload.createRoute(),    Icons.Outlined.Article),
        TopLevelDestination("More",      AppDestination.Analyze.route,                 Icons.Outlined.MoreHoriz),
        TopLevelDestination("Profile",   AppDestination.Profile.route,                 Icons.Outlined.PersonOutline)
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
        showTopBar = shouldShowAppChrome(currentRoute),
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
            composable(
                route = AppDestination.Analyze.route,
            ) {
                MoreScreen(
                    onOpenResumeMatcher = {
                        navController.navigate(AppDestination.ResumeMatcher.createRoute())
                    },
                    onOpenMockInterview = {
                        navController.navigate(AppDestination.MockInterview.createRoute())
                    },
                    onOpenCommunityFeed = {
                        navController.navigate(AppDestination.CommunityFeed.route)
                    },
                    onOpenJdIntelligence = {
                        navController.navigate(AppDestination.ResumeMatcher.createRoute(startMode = "paste"))
                    },
                    onOpenRealityCheck = {
                        navController.navigate(AppDestination.RealityCheck.route)
                    },
                    onOpenProgress = {
                        navController.navigate(AppDestination.Progress.route)
                    },
                    onOpenReferrals = {
                        navController.navigate(AppDestination.Referrals.route)
                    }
                )
            }
            composable(
                route = AppDestination.ResumeMatcher.route,
                arguments = listOf(
                    navArgument("targetJobId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("startMode") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                val viewModel: AnalysisViewModel = hiltViewModel()
                AnalysisScreen(viewModel = viewModel)
            }
            composable(AppDestination.CommunityFeed.route) {
                CommunityFeedScreen()
            }
            composable(AppDestination.RealityCheck.route) {
                RealityCheckScreen()
            }
            composable(AppDestination.Progress.route) {
                ProgressScreen()
            }
            composable(AppDestination.Referrals.route) {
                ReferralsScreen()
            }
            composable(AppDestination.Profile.route) {
                val viewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(viewModel = viewModel)
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
                    onOpenAnalysis = { jobId -> navController.navigate(AppDestination.ResumeMatcher.createRoute(jobId)) },
                    onOpenResume = { jobId -> navController.navigate(AppDestination.ResumeUpload.createRoute(jobId)) },
                    onOpenInterview = { jobId -> navController.navigate(AppDestination.MockInterview.createRoute(jobId)) }
                )
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
            composable(
                route = AppDestination.MockInterview.route,
                arguments = listOf(navArgument("targetJobId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                val viewModel: MockInterviewSetupViewModel = hiltViewModel()
                MockInterviewSetupScreen(
                    viewModel = viewModel,
                    onOpenSession = { sessionId ->
                        navController.navigate(AppDestination.MockInterviewSession.createRoute(sessionId))
                    }
                )
            }
            composable(
                route = AppDestination.MockInterviewSession.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) {
                val viewModel: MockInterviewSessionViewModel = hiltViewModel()
                MockInterviewSessionScreen(
                    viewModel = viewModel,
                    onSessionComplete = { sessionId, skippedCount ->
                        navController.navigate(AppDestination.MockInterviewSummary.createRoute(sessionId, skippedCount)) {
                            popUpTo(AppDestination.MockInterviewSession.route) {
                                inclusive = true
                            }
                        }
                    },
                    onOpenSetup = {
                        navController.navigate(AppDestination.MockInterview.createRoute())
                    }
                )
            }
            composable(
                route = AppDestination.MockInterviewSummary.route,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("skippedCount") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) {
                val viewModel: MockInterviewSummaryViewModel = hiltViewModel()
                MockInterviewSummaryScreen(
                    viewModel = viewModel,
                    onPracticeAgain = { _ ->
                        navController.navigate(AppDestination.MockInterview.createRoute()) {
                            popUpTo(AppDestination.MockInterview.route) {
                                inclusive = true
                            }
                        }
                    },
                    onOpenInterview = {
                        navController.navigate(AppDestination.MockInterview.createRoute()) {
                            popUpTo(AppDestination.MockInterview.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            composable(AppDestination.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = viewModel,
                    onOpenJobs = {
                        navController.navigate(AppDestination.Jobs.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSavedJobs = {
                        navController.navigate(AppDestination.SavedJobs.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenResumeLab = {
                        navController.navigate(AppDestination.ResumeUpload.createRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenInterview = {
                        navController.navigate(AppDestination.MockInterview.createRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenJob = { jobId -> navController.navigate(AppDestination.JobDetail.createRoute(jobId)) }
                )
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
            currentRoute == AppDestination.Onboarding.route -> AppDestination.Dashboard.route

        else -> null
    }
}

private fun shouldShowAppChrome(currentRoute: String?): Boolean {
    return when (currentRoute) {
        null,
        AppDestination.Splash.route,
        AppDestination.Login.route,
        AppDestination.Signup.route,
        AppDestination.Onboarding.route -> false

        else -> true
    }
}

private fun String?.toTitle(): String {
    return when (this) {
        AppDestination.Splash.route -> "Getting the workflow ready"
        AppDestination.Login.route -> "Sign in to your prep flow"
        AppDestination.Signup.route -> "Create your account"
        AppDestination.Onboarding.route -> "Pick your target roles"
        AppDestination.Jobs.route -> "Discover curated internships"
        AppDestination.Analyze.route -> "More"
        AppDestination.SavedJobs.route -> "Saved internships"
        AppDestination.ResumeUpload.route -> "Resume Lab"
        AppDestination.ResumeBuilder.route -> "Resume Builder"
        AppDestination.MockInterview.route -> "Interview Prep"
        AppDestination.Profile.route -> "Settings"
        AppDestination.Dashboard.route -> "Readiness dashboard"
        AppDestination.MockInterviewSession.route -> "Mock interview session"
        AppDestination.MockInterviewSummary.route -> "Interview summary"
        else -> when {
            this?.startsWith("resume/upload") == true -> "Resume Lab"
            this?.startsWith("resume/builder") == true -> "Resume Builder"
            this?.startsWith("resume/roast/") == true -> "Resume roast"
            this?.startsWith("resume-matcher") == true -> "Resume Matcher"
            this?.startsWith("job/") == true -> "Target role overview"
            this?.startsWith("analysis/") == true -> "JD reality check"
            this?.startsWith("more/community") == true -> "Community feed"
            this?.startsWith("more/reality") == true -> "Reality check"
            this?.startsWith("more/progress") == true -> "Progress"
            this?.startsWith("more/referrals") == true -> "Referrals"
            this?.startsWith("profile") == true -> "Settings"
            this?.startsWith("interview/mock/session/") == true -> "Mock interview session"
            this?.startsWith("interview/mock/summary/") == true -> "Interview summary"
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
