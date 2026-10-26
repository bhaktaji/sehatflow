package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.Role
import com.example.ui.admin.ClinicOperationalHistoryScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.doctor.DoctorConsultationHistoryScreen
import com.example.ui.patient.ClinicSelectScreen
import com.example.ui.patient.PatientLiveQueueScreen
import com.example.ui.patient.PatientViewModel
import com.example.ui.patient.UploadPrescriptionScreen
import com.example.ui.patient.VisitHistoryScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    patientViewModel: PatientViewModel = viewModel()
) {
    val currentSession by authViewModel.currentSession.collectAsStateWithLifecycle()

    val performFullLogout: () -> Unit = {
        patientViewModel.clearPatientSessionState()
        authViewModel.logout()
        navController.navigate(NavRoute.Login.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    val startDestination = when (currentSession?.role) {
        Role.PATIENT -> NavRoute.PatientHome.route
        Role.RECEPTIONIST -> NavRoute.ReceptionDashboard.route
        Role.OWNER -> NavRoute.ClinicAdminDashboard.createRoute(currentSession?.clinicId ?: "clinic_1")
        Role.DOCTOR -> NavRoute.DoctorDashboard.route
        null -> NavRoute.Login.route
    }

    androidx.compose.runtime.LaunchedEffect(currentSession) {
        val targetRoute = when (currentSession?.role) {
            Role.PATIENT -> NavRoute.PatientHome.route
            Role.RECEPTIONIST -> NavRoute.ReceptionDashboard.route
            Role.OWNER -> NavRoute.ClinicAdminDashboard.createRoute(currentSession?.clinicId ?: "clinic_1")
            Role.DOCTOR -> NavRoute.DoctorDashboard.route
            null -> NavRoute.Login.route
        }
        val currentDestination = navController.currentDestination?.route
        if (currentDestination != null && currentDestination != targetRoute) {
            val isAllowedSubRoute = currentDestination == NavRoute.Onboarding.route ||
                currentDestination.startsWith("clinic_subscription") ||
                currentDestination.startsWith("clinic_admin_history") ||
                currentDestination == NavRoute.ReceptionHistory.route ||
                currentDestination == NavRoute.DoctorConsultationHistory.route ||
                currentDestination == NavRoute.PatientVisitHistory.route ||
                (currentSession?.role == Role.OWNER && currentDestination == NavRoute.ReceptionDashboard.route) ||
                (currentSession?.role == Role.OWNER && currentDestination == NavRoute.DoctorDashboard.route)
            if (!isAllowedSubRoute || currentSession == null) {
                navController.navigate(targetRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NavRoute.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToOnboarding = { navController.navigate(NavRoute.Onboarding.route) }
            )
        }

        // --- ONBOARDING & SETUP GRAPH ---
        composable(NavRoute.Onboarding.route) {
            com.example.ui.onboarding.OnboardingScreen(
                onOnboardingComplete = { role, clinicId ->
                    when (role) {
                        com.example.data.model.UserRole.CLINIC_ADMIN -> {
                            navController.navigate(NavRoute.ClinicAdminDashboard.createRoute(clinicId)) {
                                popUpTo(NavRoute.Login.route) { inclusive = true }
                            }
                        }
                        com.example.data.model.UserRole.DOCTOR -> {
                            navController.navigate(NavRoute.DoctorDashboard.route) {
                                popUpTo(NavRoute.Login.route) { inclusive = true }
                            }
                        }
                        com.example.data.model.UserRole.RECEPTIONIST -> {
                            navController.navigate(NavRoute.ReceptionDashboard.route) {
                                popUpTo(NavRoute.Login.route) { inclusive = true }
                            }
                        }
                        com.example.data.model.UserRole.PATIENT -> {
                            navController.navigate(NavRoute.PatientHome.route) {
                                popUpTo(NavRoute.Login.route) { inclusive = true }
                            }
                        }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // --- CLINIC ADMIN & SUBSCRIPTION GRAPH ---
        composable(
            route = NavRoute.ClinicAdminDashboard.route,
            arguments = listOf(
                navArgument("clinicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val clinicId = backStackEntry.arguments?.getString("clinicId") ?: "clinic_1"
            com.example.ui.admin.ClinicAdminDashboardScreen(
                clinicId = clinicId,
                onOpenReception = { navController.navigate(NavRoute.ReceptionDashboard.route) },
                onOpenDoctor = { navController.navigate(NavRoute.DoctorDashboard.route) },
                onOpenSubscription = { navController.navigate(NavRoute.ClinicSubscription.createRoute(clinicId)) },
                onOpenOperationalHistory = { navController.navigate(NavRoute.AdminOperationalHistory.createRoute(clinicId)) },
                onLogout = performFullLogout
            )
        }

        composable(
            route = NavRoute.AdminOperationalHistory.route,
            arguments = listOf(
                navArgument("clinicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val clinicId = backStackEntry.arguments?.getString("clinicId") ?: (currentSession?.clinicId ?: "clinic_1")
            ClinicOperationalHistoryScreen(
                role = Role.OWNER,
                clinicId = clinicId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoute.ClinicSubscription.route,
            arguments = listOf(
                navArgument("clinicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val clinicId = backStackEntry.arguments?.getString("clinicId") ?: "clinic_1"
            com.example.ui.subscription.ClinicSubscriptionScreen(
                clinicId = clinicId,
                onBack = { navController.popBackStack() }
            )
        }

        // --- PATIENT NAVIGATION GRAPH ---
        composable(NavRoute.PatientHome.route) {
            ClinicSelectScreen(
                viewModel = patientViewModel,
                onDoctorSelected = { clinicId, doctorId ->
                    navController.navigate(NavRoute.PrescriptionUpload.createRoute(clinicId, doctorId))
                },
                onViewHistory = {
                    navController.navigate(NavRoute.PatientVisitHistory.route)
                },
                onViewLiveToken = { tokenId, sessionId ->
                    navController.navigate(NavRoute.PatientLiveQueue.createRoute(tokenId, sessionId))
                },
                onLogout = performFullLogout
            )
        }

        composable(
            route = NavRoute.PrescriptionUpload.route,
            arguments = listOf(
                navArgument("clinicId") { type = NavType.StringType },
                navArgument("doctorId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val clinicId = backStackEntry.arguments?.getString("clinicId") ?: ""
            val doctorId = backStackEntry.arguments?.getString("doctorId") ?: ""

            UploadPrescriptionScreen(
                viewModel = patientViewModel,
                clinicId = clinicId,
                doctorId = doctorId,
                onBack = { navController.popBackStack() },
                onTokenRequested = { tokenId, sessionId ->
                    navController.navigate(NavRoute.PatientLiveQueue.createRoute(tokenId, sessionId)) {
                        popUpTo(NavRoute.PatientHome.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = NavRoute.PatientLiveQueue.route,
            arguments = listOf(
                navArgument("tokenId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tokenId = backStackEntry.arguments?.getString("tokenId") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PatientLiveQueueScreen(
                viewModel = patientViewModel,
                tokenId = tokenId,
                sessionId = sessionId,
                onReuploadPrescription = { clinicId, doctorId ->
                    navController.navigate(NavRoute.PrescriptionUpload.createRoute(clinicId, doctorId))
                },
                onViewHistory = {
                    navController.navigate(NavRoute.PatientVisitHistory.route)
                },
                onLogout = performFullLogout
            )
        }

        composable(NavRoute.PatientVisitHistory.route) {
            VisitHistoryScreen(
                viewModel = patientViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- RECEPTIONIST & DOCTOR NAVIGATION GRAPH ---
        composable(NavRoute.ReceptionDashboard.route) {
            val receptionViewModel: com.example.ui.receptionist.ReceptionistViewModel = viewModel()
            com.example.ui.receptionist.ReceptionDashboardScreen(
                viewModel = receptionViewModel,
                onLogout = performFullLogout,
                onNavigateToAdmin = {
                    val clinicId = currentSession?.clinicId ?: "clinic_1"
                    navController.navigate(NavRoute.ClinicAdminDashboard.createRoute(clinicId))
                },
                onViewHistory = {
                    navController.navigate(NavRoute.ReceptionHistory.route)
                }
            )
        }

        composable(NavRoute.ReceptionHistory.route) {
            ClinicOperationalHistoryScreen(
                role = Role.RECEPTIONIST,
                clinicId = currentSession?.clinicId ?: "clinic_1",
                onBack = { navController.popBackStack() }
            )
        }

        // --- DOCTOR NAVIGATION GRAPH ---
        composable(NavRoute.DoctorDashboard.route) {
            val doctorViewModel: com.example.ui.doctor.DoctorViewModel = viewModel()
            com.example.ui.doctor.DoctorDashboardScreen(
                viewModel = doctorViewModel,
                onViewWaitingList = { sessionId ->
                    navController.navigate(NavRoute.DoctorWaitingList.createRoute(sessionId))
                },
                onViewConsultationHistory = {
                    navController.navigate(NavRoute.DoctorConsultationHistory.route)
                },
                onLogout = performFullLogout
            )
        }

        composable(NavRoute.DoctorConsultationHistory.route) {
            val doctorViewModel: com.example.ui.doctor.DoctorViewModel = viewModel()
            DoctorConsultationHistoryScreen(
                viewModel = doctorViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoute.DoctorWaitingList.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) {
            val doctorViewModel: com.example.ui.doctor.DoctorViewModel = viewModel()
            com.example.ui.doctor.DoctorWaitingQueueScreen(
                viewModel = doctorViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
