package com.example.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.QueueToken
import com.example.domain.model.TokenStatus
import com.example.ui.components.OfflineBanner
import com.example.ui.components.ReasonDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.ZoomablePrescriptionViewer
import com.example.ui.theme.MintGreen
import com.example.ui.theme.RedError
import com.example.ui.theme.Teal40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDashboardScreen(
    viewModel: DoctorViewModel,
    onViewWaitingList: (sessionId: String) -> Unit,
    onLogout: () -> Unit,
    onViewConsultationHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSkipDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }

    val currentPatient = uiState.currentPatient
    val session = uiState.currentSession

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doctor_dashboard_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = onViewConsultationHistory,
                        modifier = Modifier.testTag("doctor_top_history_btn")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Consultation History")
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("doctor_logout_btn")) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OfflineBanner(isOffline = uiState.isOffline)

            // Action / Error Status Banner
            val statusMsg = uiState.errorMessage ?: uiState.successMessage
            if (statusMsg != null) {
                val isErr = uiState.errorMessage != null
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("doctor_status_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isErr) MaterialTheme.colorScheme.errorContainer else MintGreen.copy(alpha = 0.16f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMsg,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MintGreen,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // Doctor Status / Break Banner
            if (session != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.status == "PAUSED") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (session.doctorName.startsWith("Dr.")) session.doctorName else "Dr. ${session.doctorName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (session.status == "PAUSED") "Currently on Break / Paused" else "Consultations Active",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (session.status == "PAUSED") {
                                    viewModel.toggleBreak(null)
                                } else {
                                    showPauseDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (session.status == "PAUSED") MintGreen else MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("doctor_pause_resume_btn")
                        ) {
                            Text(if (session.status == "PAUSED") "Resume" else "Take Break")
                        }
                    }
                }
            }

            // Current Patient Card
            Card(
                modifier = Modifier.fillMaxWidth().testTag("current_patient_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CURRENT PATIENT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentPatient != null) {
                        Text(
                            text = currentPatient.label,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("doctor_current_token_label")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentPatient.displayName ?: "Patient",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Patient ID: ${currentPatient.patientIdentifier ?: "PAT-1001"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("doctor_current_patient_id_badge")
                        )
                        if (!currentPatient.presentingComplaint.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Presenting Problem: ${currentPatient.presentingComplaint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        StatusBadge(status = currentPatient.status)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Requirement 2.2: Authorized Patient Medical History Timeline Action
                        OutlinedButton(
                            onClick = {
                                viewModel.openPatientMedicalTimeline(
                                    patientIdOrIdentifier = currentPatient.patientIdentifier ?: currentPatient.patientId ?: "PAT-1001",
                                    patientDisplayName = currentPatient.displayName
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("view_patient_medical_history_btn")
                        ) {
                            Icon(Icons.Default.MedicalInformation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Patient Medical History")
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Verified Prescription Action
                        if (currentPatient.prescriptionId != null) {
                            OutlinedButton(
                                onClick = { viewModel.viewVerifiedPrescription(currentPatient) },
                                modifier = Modifier.fillMaxWidth().testTag("view_rx_button")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.view_verified_rx_btn))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Consultation Lifecycle Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (currentPatient.status == TokenStatus.CALLED) {
                                Button(
                                    onClick = { viewModel.startConsultation(currentPatient.id) },
                                    enabled = !uiState.isActionBusy,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("start_consultation_btn")
                                ) {
                                    Text(stringResource(R.string.start_consultation_btn))
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.completeConsultation(currentPatient.id) },
                                    enabled = !uiState.isActionBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("complete_consultation_btn")
                                ) {
                                    Text(stringResource(R.string.complete_consultation_btn))
                                }
                            }

                            OutlinedButton(
                                onClick = { showSkipDialog = true },
                                enabled = !uiState.isActionBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("skip_patient_btn")
                            ) {
                                Text(stringResource(R.string.skip_btn))
                            }
                        }
                    } else {
                        Text(
                            text = "No patient in room",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.callNext() },
                            enabled = !uiState.isActionBusy && (uiState.currentSession?.status != "PAUSED"),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("call_next_btn")
                        ) {
                            if (uiState.isActionBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.call_next_btn))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waiting Line Shortcut Button
            Button(
                onClick = {
                    session?.let { onViewWaitingList(it.id) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("view_waiting_list_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Waiting Patients (${uiState.waitingTokens.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Consultation History Shortcut Button (Phase 5)
            Button(
                onClick = onViewConsultationHistory,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("doctor_consultation_history_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Consultation History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Past consultations, clinical notes, Rx & multi-clinic filters",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Today's Consultation Statistics
            Text(
                text = "Today's Consultation Stats",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            val stats = uiState.stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).testTag("stat_seen_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (stats?.patientsSeenToday ?: 0).toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "Patients Seen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).testTag("stat_duration_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f m", stats?.averageConsultationMinutes ?: 10.0),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Teal40)
                        )
                        Text(
                            text = "Avg Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Skip Patient Dialog
        if (showSkipDialog && currentPatient != null) {
            ReasonDialog(
                title = "Skip Patient ${currentPatient.label}",
                confirmLabel = "Skip Patient",
                descriptionText = "Confirm skipping patient. The patient will be notified to speak with reception.",
                onConfirm = { reason ->
                    viewModel.skipPatient(currentPatient.id, reason)
                    showSkipDialog = false
                },
                onDismiss = { showSkipDialog = false }
            )
        }

        // Doctor Break Reason Dialog
        if (showPauseDialog) {
            ReasonDialog(
                title = "Pause Consultations / Take Break",
                confirmLabel = "Pause Consultations",
                descriptionText = "Enter a short explanation for the queue display (e.g. 15 minute tea break, emergency call).",
                onConfirm = { message ->
                    viewModel.toggleBreak(message)
                    showPauseDialog = false
                },
                onDismiss = { showPauseDialog = false }
            )
        }

        // Prescription Modal Sheet
        val rxUrl = uiState.selectedPrescriptionUrl
        if (rxUrl != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissPrescriptionModal() },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verified Prescription • Token ${uiState.selectedPrescriptionTokenLabel ?: ""}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { viewModel.dismissPrescriptionModal() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ZoomablePrescriptionViewer(imageUrl = rxUrl)
                }
            }
        }

        // Requirement 2.2: Patient Medical History Timeline Modal
        val timelineLabel = uiState.timelinePatientLabel
        if (timelineLabel != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPatientMedicalTimeline() },
                modifier = Modifier.testTag("doctor_patient_timeline_dialog"),
                title = {
                    Column {
                        Text("Patient Medical Record Timeline", fontWeight = FontWeight.Bold)
                        Text(
                            text = timelineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.errorMessage != null && uiState.patientMedicalTimeline.isEmpty()) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (uiState.patientMedicalTimeline.isEmpty()) {
                            Text("No prior medical records within the 2-year clinical access window.")
                        } else {
                            Text(
                                text = "Showing ${uiState.patientMedicalTimeline.size} completed consultation(s) within the 2-year clinical window (phone number redacted per role policy):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            uiState.patientMedicalTimeline.forEach { rec ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("timeline_record_item_${rec.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${rec.consultationDate} (${rec.consultationTime}) • ${rec.clinicName} (${rec.chamber})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Doctor: ${rec.doctorName} (${rec.doctorSpecialty}) • Status: ${rec.consultationStatus}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            text = "Presenting Problem: ${rec.presentingComplaint ?: "Not recorded"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Diagnosis: ${rec.diagnosis ?: "Not recorded"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Clinical Notes: ${rec.clinicalNotes ?: "Not recorded"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Prescription: ${rec.prescriptionSummary ?: "Not recorded"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (!rec.allergiesOrMedicalHistory.isNullOrBlank()) {
                                            Text(
                                                text = "Allergies / History: ${rec.allergiesOrMedicalHistory}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Text(
                                            text = "Follow-up: ${rec.followUpRecommendations ?: "Not recorded"}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.dismissPatientMedicalTimeline() },
                        modifier = Modifier.testTag("close_patient_timeline_btn")
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
