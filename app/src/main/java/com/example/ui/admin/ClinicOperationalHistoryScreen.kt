package com.example.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.di.ServiceLocator
import com.example.domain.model.*
import com.example.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicOperationalHistoryScreen(
    role: Role,
    clinicId: String = "clinic_xyz",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val historyRepo = remember { ServiceLocator.consultationHistoryRepository }
    val session = remember(role, clinicId) {
        UserSession(
            token = "session_operational_history",
            userId = if (role == Role.OWNER) "owner_1" else "recep_1",
            displayName = if (role == Role.OWNER) "Clinic Administrator" else "Front Desk Receptionist",
            role = role,
            clinicId = clinicId,
            doctorId = null
        )
    }

    var criteria by remember {
        mutableStateOf(HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS))
    }

    val filteredResult = remember(criteria, session) {
        historyRepo.queryHistory(session = session, criteria = criteria)
    }

    val operationalSummary = remember(criteria, session) {
        historyRepo.getClinicOperationalSummary(session = session, criteria = criteria)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (role == Role.OWNER) "Clinic Operational History" else "Reception Booking & Token History",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clinic Scope: $clinicId • ${filteredResult.activeWindowLabel} (Read-Only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("operational_history_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS) },
                        modifier = Modifier.testTag("operational_clear_filters_btn")
                    ) {
                        Text("Reset")
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
        ) {
            // Operational Summary Metrics Banner (Phase 6)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("operational_summary_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatItem("Total Requests", operationalSummary.totalAppointmentRequests.toString())
                        SummaryStatItem("Consultations", operationalSummary.completedConsultationsCount.toString())
                        SummaryStatItem("Cancelled", operationalSummary.cancelledCount.toString())
                        SummaryStatItem("No-Show/Rej", (operationalSummary.noShowCount + operationalSummary.rejectedCount).toString())
                    }
                    if (role == Role.OWNER && operationalSummary.utilizationByChamber.isNotEmpty()) {
                        HorizontalDivider()
                        val chamberBreakdown = operationalSummary.utilizationByChamber.entries.joinToString(" • ") { "${it.key}: ${it.value}" }
                        Text(
                            text = "Chamber Utilization: $chamberBreakdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search by Patient Name, Patient ID, or Token Number
            OutlinedTextField(
                value = criteria.searchQuery,
                onValueChange = { criteria = criteria.copy(searchQuery = it, page = 1) },
                label = { Text("Search by Patient Name, ID, or Token (e.g. A-04, C-03)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (criteria.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { criteria = criteria.copy(searchQuery = "", page = 1) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("operational_history_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Date Range Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HistoryDateRangePreset.LAST_2_YEARS,
                    HistoryDateRangePreset.LAST_3_MONTHS,
                    HistoryDateRangePreset.LAST_30_DAYS,
                    HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
                ).forEach { preset ->
                    FilterChip(
                        selected = criteria.dateRangePreset == preset,
                        onClick = { criteria = criteria.copy(dateRangePreset = preset, page = 1) },
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("op_filter_date_${preset.name}")
                    )
                }
            }

            // Doctor & Chamber Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All Doctors", "doc_john" to "Dr. John", "doc_donna" to "Dr. Donna", "doc_alex" to "Dr. Alex").forEach { (docKey, label) ->
                    FilterChip(
                        selected = criteria.doctorIdFilter == docKey,
                        onClick = { criteria = criteria.copy(doctorIdFilter = docKey, page = 1) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("op_filter_doc_$docKey")
                    )
                }

                listOf("ALL" to "All Chambers", "Chamber A" to "Chamber A", "Chamber B" to "Chamber B", "Chamber C" to "Chamber C").forEach { (chKey, label) ->
                    FilterChip(
                        selected = criteria.chamberFilter == chKey,
                        onClick = { criteria = criteria.copy(chamberFilter = chKey, page = 1) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("op_filter_chamber_$chKey")
                    )
                }
            }

            // Appointment / Attendance Status Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Statuses",
                    "COMPLETED" to "Completed Visits",
                    "CANCELLED" to "Cancelled",
                    "NO_SHOW" to "No-Shows",
                    "REJECTED" to "Rejected Rx"
                ).forEach { (stKey, label) ->
                    FilterChip(
                        selected = criteria.appointmentStatusFilter == stKey,
                        onClick = { criteria = criteria.copy(appointmentStatusFilter = stKey, page = 1) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("op_filter_status_$stKey")
                    )
                }
            }

            if (filteredResult.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("operational_history_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical booking or token records match the selected filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("operational_history_list")
                ) {
                    items(filteredResult.records, key = { it.id }) { record ->
                        OperationalHistoryItemCard(record = record)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OperationalHistoryItemCard(record: ConsultationRecord) {
    val isConsultation = record.recordCategory == RecordCategory.CONSULTATION
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("op_history_card_${record.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Token ${record.tokenLabel} • ${record.patientName} (${record.patientIdentifier})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                StatusBadge(status = record.appointmentStatus)
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!record.patientPhone.isNullOrBlank()) {
                Text(
                    text = "Phone: ${record.patientPhone}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!record.presentingComplaint.isNullOrBlank()) {
                Text(
                    text = "Reason for Visit: ${record.presentingComplaint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Doctor: ${record.doctorName} • ${record.clinicName} (${record.chamber})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Date: ${record.consultationDate} at ${record.consultationTime} • Check-in: ${record.checkInStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isConsultation) {
                    "Consultation Outcome: ${record.consultationStatus} (Clinical Notes Protected / Read-Only)"
                } else {
                    "Booking Outcome: ${record.consultationStatus}${record.cancellationOrRejectionReason?.let { " — $it" } ?: ""}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isConsultation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
