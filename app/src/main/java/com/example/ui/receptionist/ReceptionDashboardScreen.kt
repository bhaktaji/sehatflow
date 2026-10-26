package com.example.ui.receptionist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.domain.model.ClinicDoctor
import com.example.domain.model.PendingPrescription
import com.example.domain.model.QueueToken
import com.example.domain.model.TokenStatus
import com.example.ui.components.OfflineBanner
import com.example.ui.components.ReasonDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.MintGreen
import com.example.ui.theme.RedError
import com.example.ui.theme.Teal40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceptionDashboardScreen(
    viewModel: ReceptionistViewModel,
    onLogout: () -> Unit,
    onNavigateToAdmin: (() -> Unit)? = null,
    onViewHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Live Queue, 1: Pending Rx
    var patientSearchQuery by remember { mutableStateOf("") }

    var showWalkInDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var tokenToReorder by remember { mutableStateOf<QueueToken?>(null) }
    var tokenToChangePriority by remember { mutableStateOf<QueueToken?>(null) }
    var tokenToMarkNoShow by remember { mutableStateOf<QueueToken?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reception_dashboard_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = onViewHistory,
                        modifier = Modifier.testTag("reception_history_btn")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Booking & Token History")
                    }
                    if (onNavigateToAdmin != null) {
                        IconButton(
                            onClick = onNavigateToAdmin,
                            modifier = Modifier.testTag("btn_reception_to_admin")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Clinic Admin Hub")
                        }
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("reception_logout_btn")) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showWalkInDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Walk-in Patient") },
                modifier = Modifier.testTag("walk_in_fab")
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOffline = uiState.isOffline)

            val statusMsg = uiState.errorMessage ?: uiState.successMessage
            if (statusMsg != null) {
                val isErr = uiState.errorMessage != null
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("reception_status_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isErr) MaterialTheme.colorScheme.errorContainer else MintGreen.copy(alpha = 0.16f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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

            // Session selector / Doctor availability bar
            val session = uiState.selectedSession
            if (session != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.status == "PAUSED") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (session.doctorName.startsWith("Dr.")) session.doctorName else "Dr. ${session.doctorName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Status: ${session.status} • Prefix: ${session.tokenPrefix}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (session.status == "PAUSED") {
                                    viewModel.toggleDoctorPause(null)
                                } else {
                                    showPauseDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (session.status == "PAUSED") MintGreen else MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("toggle_pause_btn")
                        ) {
                            Text(if (session.status == "PAUSED") "Resume" else "Pause / Break")
                        }
                    }
                }
            }

            // Top Metrics Overview Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Waiting",
                    count = uiState.waitingCount,
                    color = Teal40,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "In Room",
                    count = uiState.inConsultationCount,
                    color = MintGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Done",
                    count = uiState.completedCount,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "No-Show",
                    count = uiState.noShowCount,
                    color = RedError,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tabs: Live Queue vs Pending Prescriptions / Requests
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("reception_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_live_queue)) },
                    modifier = Modifier.testTag("live_queue_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (uiState.pendingPrescriptions.isNotEmpty())
                                "Pending Rx (${uiState.pendingPrescriptions.size})"
                            else
                                "Pending Rx"
                        )
                    },
                    modifier = Modifier.testTag("pending_rx_tab")
                )
            }

            // Quick Search by Patient ID, Name, Phone, or Token
            OutlinedTextField(
                value = patientSearchQuery,
                onValueChange = { patientSearchQuery = it },
                label = { Text("Search Patient ID (PAT-1001), Name, Phone, or Token") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (patientSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { patientSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("reception_patient_search_input"),
                shape = RoundedCornerShape(10.dp)
            )

            // Chamber Filter & UAT Helper Toolbar
            var selectedChamberFilter by remember { mutableStateOf("All Chambers") }
            val chamberOptions = listOf("All Chambers", "Chamber A (Dr. John)", "Chamber B (Dr. Donna)", "Chamber C (Dr. Alex)")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollableTabRow(
                    selectedTabIndex = chamberOptions.indexOf(selectedChamberFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f).testTag("chamber_filter_row")
                ) {
                    chamberOptions.forEach { chamberName ->
                        Tab(
                            selected = selectedChamberFilter == chamberName,
                            onClick = { selectedChamberFilter = chamberName },
                            text = { Text(chamberName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("chamber_chip_${chamberName.take(9).replace(" ", "_")}")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        com.example.core.demo.DemoDataProvider.populateLargeQueueForTesting(35)
                        viewModel.loadSessions()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_populate_35_uat")
                ) {
                    Text("UAT: 35 Q", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Content Area
            val normalizedQuery = patientSearchQuery.trim().lowercase()
            if (selectedTab == 0) {
                // Live Queue List
                val waitingAndActive = uiState.tokens.filter { !it.status.isTerminal }
                val filteredTokens = waitingAndActive.filter { token ->
                    val matchesChamber = when (selectedChamberFilter) {
                        "Chamber A (Dr. John)" -> token.chamber == "Chamber A"
                        "Chamber B (Dr. Donna)" -> token.chamber == "Chamber B"
                        "Chamber C (Dr. Alex)" -> token.chamber == "Chamber C"
                        else -> true
                    }
                    val matchesSearch = normalizedQuery.isEmpty() ||
                        (token.patientIdentifier?.lowercase()?.contains(normalizedQuery) == true) ||
                        (token.displayName?.lowercase()?.contains(normalizedQuery) == true) ||
                        (token.patientPhone?.lowercase()?.contains(normalizedQuery) == true) ||
                        token.label.lowercase().contains(normalizedQuery)
                    matchesChamber && matchesSearch
                }

                if (filteredTokens.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No patients currently in the waiting line for this filter.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("reception_queue_list")
                    ) {
                        items(filteredTokens) { token ->
                            ReceptionTokenCard(
                                token = token,
                                onPriorityClick = { tokenToChangePriority = token },
                                onReorderClick = { tokenToReorder = token },
                                onNoShowClick = { tokenToMarkNoShow = token }
                            )
                        }
                    }
                }
            } else {
                // Pending Prescriptions & Unassigned Requests List
                val filteredPending = uiState.pendingPrescriptions.filter { pending ->
                    normalizedQuery.isEmpty() ||
                        pending.patientIdentifier.lowercase().contains(normalizedQuery) ||
                        pending.patientName.lowercase().contains(normalizedQuery) ||
                        (pending.patientPhone?.lowercase()?.contains(normalizedQuery) == true) ||
                        (pending.presentingComplaint?.lowercase()?.contains(normalizedQuery) == true)
                }

                if (filteredPending.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No prescriptions or doctor assignment requests currently awaiting review.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("pending_prescriptions_list")
                    ) {
                        items(filteredPending) { pending ->
                            PendingPrescriptionCard(
                                pending = pending,
                                onReviewClick = { viewModel.selectPrescriptionForReview(pending) },
                                onAssignDoctorClick = { viewModel.openAssignDoctorDialog(pending) }
                            )
                        }
                    }
                }
            }
        }

        // Prescription Review Sheet
        val pendingRx = uiState.selectedPrescription
        if (pendingRx != null) {
            PrescriptionReviewSheet(
                pending = pendingRx,
                signedImageUrl = uiState.signedPrescriptionUrl,
                isBusy = uiState.isActionBusy,
                onApprove = { viewModel.approvePrescription(pendingRx.tokenId) },
                onReject = { reason -> viewModel.rejectPrescription(pendingRx.tokenId, reason) },
                onDismiss = { viewModel.dismissPrescriptionReview() }
            )
        }

        // Assign Doctor Dialog for complaint-based / unassigned requests
        val requestToAssign = uiState.requestToAssignDoctor
        if (requestToAssign != null) {
            AssignDoctorDialog(
                pending = requestToAssign,
                availableDoctors = uiState.availableDoctors,
                errorMessage = uiState.errorMessage,
                onAssignDoctor = { doctorId ->
                    viewModel.assignDoctorToRequest(
                        tokenId = requestToAssign.tokenId,
                        targetDoctorId = doctorId,
                        requestedClinicName = requestToAssign.requestedClinic ?: "XYZ Clinic",
                        requestedDay = requestToAssign.requestedDate ?: "Monday"
                    )
                },
                onDismiss = { viewModel.dismissAssignDoctorDialog() }
            )
        }

        // Add Walk-in Dialog
        if (showWalkInDialog) {
            AddWalkInDialog(
                onConfirm = { name, isPriority ->
                    viewModel.addWalkIn(name, isPriority)
                    showWalkInDialog = false
                },
                onDismiss = { showWalkInDialog = false }
            )
        }

        // Reorder Queue Dialog
        tokenToReorder?.let { token ->
            ReorderQueueDialog(
                token = token,
                maxPosition = uiState.tokens.size,
                onConfirm = { targetPos, reason ->
                    viewModel.reorderQueue(token.id, targetPos, reason)
                    tokenToReorder = null
                },
                onDismiss = { tokenToReorder = null }
            )
        }

        // Priority Change Dialog
        tokenToChangePriority?.let { token ->
            val newEnabled = !token.priority
            ReasonDialog(
                title = if (newEnabled) "Mark Emergency Priority" else "Remove Priority",
                confirmLabel = "Confirm",
                descriptionText = "Changing token ${token.label} priority requires a reason for the audit log.",
                onConfirm = { reason ->
                    viewModel.togglePriority(token.id, newEnabled, reason)
                    tokenToChangePriority = null
                },
                onDismiss = { tokenToChangePriority = null }
            )
        }

        // Mark No-Show Dialog
        tokenToMarkNoShow?.let { token ->
            ReasonDialog(
                title = "Mark Token ${token.label} as No-Show",
                confirmLabel = "Mark No-Show",
                descriptionText = "Confirm patient did not arrive after grace period.",
                onConfirm = { reason ->
                    viewModel.rejectPrescription(token.id, reason)
                    tokenToMarkNoShow = null
                },
                onDismiss = { tokenToMarkNoShow = null }
            )
        }

        // Doctor Pause Reason Dialog
        if (showPauseDialog) {
            ReasonDialog(
                title = "Doctor Break / Pause Session",
                confirmLabel = "Pause Session",
                descriptionText = "Enter an optional break or delay message displayed on patient ETA cards.",
                onConfirm = { message ->
                    viewModel.toggleDoctorPause(message)
                    showPauseDialog = false
                },
                onDismiss = { showPauseDialog = false }
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReceptionTokenCard(
    token: QueueToken,
    onPriorityClick: () -> Unit,
    onReorderClick: () -> Unit,
    onNoShowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("reception_token_item_${token.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = token.label,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    if (token.priority) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMERGENCY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RedError,
                            modifier = Modifier
                                .background(RedError.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                StatusBadge(status = token.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Patient: ${token.displayName ?: "Walk-in"} (${token.patientIdentifier ?: "PAT-0000"}) • Queue #${token.queuePosition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${token.chamber ?: "Chamber A"} • ${token.appointmentDate ?: "Today"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!token.patientPhone.isNullOrBlank()) {
                Text(
                    text = "Phone: ${token.patientPhone}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!token.presentingComplaint.isNullOrBlank()) {
                Text(
                    text = "Reason for Visit: ${token.presentingComplaint}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (token.isRescheduled) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📅 Rescheduled from ${token.originalRequestedDate ?: "Monday"} (10/10 capacity limit met). Assigned to ${token.appointmentDate ?: "Tuesday"}.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AmberWarning,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onPriorityClick, modifier = Modifier.size(36.dp).testTag("priority_action_btn")) {
                    Icon(
                        imageVector = if (token.priority) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Priority",
                        tint = if (token.priority) RedError else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onReorderClick, modifier = Modifier.size(36.dp).testTag("reorder_action_btn")) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Reorder Position")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onNoShowClick, modifier = Modifier.size(36.dp).testTag("no_show_action_btn")) {
                    Icon(Icons.Default.PersonOff, contentDescription = "Mark No Show")
                }
            }
        }
    }
}

@Composable
private fun PendingPrescriptionCard(
    pending: PendingPrescription,
    onReviewClick: () -> Unit,
    onAssignDoctorClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("pending_rx_card_${pending.prescriptionId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${pending.patientName} (${pending.patientIdentifier})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (!pending.patientPhone.isNullOrBlank()) {
                    Text(
                        text = "Phone: ${pending.patientPhone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!pending.presentingComplaint.isNullOrBlank()) {
                    Text(
                        text = "Complaint: ${pending.presentingComplaint}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (pending.needsDoctorAssignment) {
                        "Needs Doctor Assignment • Submitted: ${pending.requestedAt?.take(10) ?: "Today"}"
                    } else {
                        "Submitted: ${pending.requestedAt?.take(10) ?: "Today"} • Format: ${pending.fileType.substringAfterLast("/")}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pending.needsDoctorAssignment) {
                    Button(
                        onClick = onAssignDoctorClick,
                        modifier = Modifier.testTag("assign_doctor_btn_${pending.tokenId}")
                    ) {
                        Text("Assign Doctor")
                    }
                }
                if (!pending.prescriptionId.startsWith("NO_RX")) {
                    OutlinedButton(
                        onClick = onReviewClick,
                        modifier = Modifier.testTag("review_rx_btn")
                    ) {
                        Text("Review Rx")
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignDoctorDialog(
    pending: PendingPrescription,
    availableDoctors: List<com.example.domain.model.ClinicDoctor>,
    errorMessage: String? = null,
    onAssignDoctor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reqClinic = pending.requestedClinic ?: "XYZ Clinic"
    val reqDay = pending.requestedDate ?: "Monday"

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("assign_doctor_dialog"),
        title = {
            Text(
                text = "Assign Doctor for ${pending.patientName} (${pending.patientIdentifier})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Presenting Complaint: ${pending.presentingComplaint ?: "Not recorded"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Select an available specialist matching the patient's complaint, schedule, and remaining capacity:",
                    style = MaterialTheme.typography.bodySmall
                )
                availableDoctors.forEach { doctor ->
                    val activeDays = doctor.schedules.filter { it.isDoctorAvailable && it.dailyCapacity > 0 }.map { it.dayOfWeek }
                    val approvedCount = com.example.core.demo.DemoDataProvider.getApprovedCountFor(doctor.id, reqClinic, reqDay)
                    val totalCap = com.example.core.demo.DemoDataProvider.getCapacityFor(doctor.id, reqClinic, reqDay)
                    val remaining = (totalCap - approvedCount).coerceAtLeast(0)
                    val slotStatusLabel = if (totalCap > 0 && remaining > 0) {
                        "$reqClinic • $reqDay: $remaining/$totalCap slots available"
                    } else if (totalCap > 0) {
                        "$reqClinic • $reqDay Full ($approvedCount/$totalCap) -> Auto-assigns next available day"
                    } else {
                        "Primary Clinic: ${doctor.primaryClinic}"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAssignDoctor(doctor.id) }
                            .testTag("assign_doctor_option_${doctor.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${doctor.displayName} (${doctor.specialty})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${doctor.primaryChamber} • Days: ${activeDays.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = slotStatusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (remaining > 0) MintGreen else AmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onAssignDoctor(doctor.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("assign_doctor_confirm_btn_${doctor.id}")
                            ) {
                                Text("Assign", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
