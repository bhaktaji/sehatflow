package com.example.ui.navigation

sealed class NavRoute(val route: String) {
    // Auth & Onboarding
    data object Login : NavRoute("auth_login")
    data object Onboarding : NavRoute("onboarding_role")

    // Admin & Subscription
    data object ClinicAdminDashboard : NavRoute("clinic_admin_dashboard/{clinicId}") {
        fun createRoute(clinicId: String) = "clinic_admin_dashboard/$clinicId"
    }
    data object ClinicSubscription : NavRoute("clinic_subscription/{clinicId}") {
        fun createRoute(clinicId: String) = "clinic_subscription/$clinicId"
    }

    // Patient Graph
    data object PatientHome : NavRoute("patient_home")
    data object ClinicSelect : NavRoute("patient_clinic_select")
    data object PrescriptionUpload : NavRoute("patient_rx_upload/{clinicId}/{doctorId}") {
        fun createRoute(clinicId: String, doctorId: String) = "patient_rx_upload/$clinicId/$doctorId"
    }
    data object PatientLiveQueue : NavRoute("patient_live_queue/{tokenId}/{sessionId}") {
        fun createRoute(tokenId: String, sessionId: String) = "patient_live_queue/$tokenId/$sessionId"
    }
    data object PatientVisitHistory : NavRoute("patient_visit_history")

    // Receptionist Graph
    data object ReceptionDashboard : NavRoute("reception_dashboard")
    data object ReceptionPendingRx : NavRoute("reception_pending_rx/{sessionId}") {
        fun createRoute(sessionId: String) = "reception_pending_rx/$sessionId"
    }
    data object ReceptionHistory : NavRoute("reception_history")

    // Doctor Graph
    data object DoctorDashboard : NavRoute("doctor_dashboard")
    data object DoctorWaitingList : NavRoute("doctor_waiting_list/{sessionId}") {
        fun createRoute(sessionId: String) = "doctor_waiting_list/$sessionId"
    }
    data object DoctorStats : NavRoute("doctor_stats/{sessionId}") {
        fun createRoute(sessionId: String) = "doctor_stats/$sessionId"
    }
    data object DoctorConsultationHistory : NavRoute("doctor_consultation_history")

    // Admin Operational History
    data object AdminOperationalHistory : NavRoute("admin_operational_history/{clinicId}") {
        fun createRoute(clinicId: String) = "admin_operational_history/$clinicId"
    }
}
