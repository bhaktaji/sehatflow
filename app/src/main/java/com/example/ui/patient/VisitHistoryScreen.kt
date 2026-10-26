package com.example.ui.patient

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.*
import com.example.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitHistoryScreen(
    viewModel: PatientViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val criteria = uiState.historyCriteria
    val filteredResult = uiState.filteredHistory

    LaunchedEffect(Unit) {
        viewModel.updateHistoryCriteria(criteria)
    }

    val selectedDetail = uiState.selectedHistoryRecord
    if (selectedDetail != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHistoryDetail() },
            modifier = Modifier.testTag("patient_consultation_detail_dialog"),
            title = {
                Column {
                    Text(
                        text = if (selectedDetail.recordCategory == RecordCategory.CONSULTATION) {
                            "Consultation Summary • ${selectedDetail.tokenLabel}"
                        } else {
                            "Appointment Record • ${selectedDetail.tokenLabel}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedDetail.consultationDate} at ${selectedDetail.consultationTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Doctor: ${selectedDetail.doctorName} (${selectedDetail.doctorSpecialty})", fontWeight = FontWeight.SemiBold)
                    Text("Location: ${selectedDetail.clinicName} • ${selectedDetail.chamber}")
                    Text("Status: ${selectedDetail.consultationStatus} (${selectedDetail.appointmentStatus.name})")
                    if (selectedDetail.diagnosis != null) {
                        HorizontalDivider()
                        Text("Diagnosis: ${selectedDetail.diagnosis}", fontWeight = FontWeight.Medium)
                    }
                    if (selectedDetail.clinicalNotes != null) {
                        Text("Clinical Notes: ${selectedDetail.clinicalNotes}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedDetail.prescriptionSummary != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Prescription Issued", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(selectedDetail.prescriptionSummary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (selectedDetail.followUpRecommendations != null) {
                        Text("Follow-up: ${selectedDetail.followUpRecommendations}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedDetail.cancellationOrRejectionReason != null) {
                        Text(
                            "Note: ${selectedDetail.cancellationOrRejectionReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissHistoryDetail() },
                    modifier = Modifier.testTag("close_patient_history_detail_btn")
                ) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.visit_history_btn), fontWeight = FontWeight.Bold)
                        Text(
                            text = "Window: ${filteredResult.activeWindowLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.resetHistoryFilters() },
                        modifier = Modifier.testTag("patient_clear_history_filters_btn")
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
            // Search & Filter Controls
            OutlinedTextField(
                value = criteria.searchQuery,
                onValueChange = { viewModel.updateHistoryCriteria(criteria.copy(searchQuery = it, page = 1)) },
                label = { Text("Search by Doctor, Clinic, Diagnosis, or Token") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (criteria.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateHistoryCriteria(criteria.copy(searchQuery = "", page = 1)) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("patient_history_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Date Range Preset Row (Default: Last 3 Months; Toggleable to All Time / Archived)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HistoryDateRangePreset.LAST_3_MONTHS,
                    HistoryDateRangePreset.LAST_30_DAYS,
                    HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
                ).forEach { preset ->
                    FilterChip(
                        selected = criteria.dateRangePreset == preset,
                        onClick = {
                            viewModel.updateHistoryCriteria(criteria.copy(dateRangePreset = preset, page = 1))
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("patient_filter_date_${preset.name}")
                    )
                }
            }

            // Record Category & Clinic / Prescription Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = criteria.recordCategoryFilter == null,
                    onClick = { viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = null, page = 1)) },
                    label = { Text("All Records") },
                    modifier = Modifier.testTag("patient_filter_type_all")
                )
                FilterChip(
                    selected = criteria.recordCategoryFilter == RecordCategory.CONSULTATION,
                    onClick = { viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = RecordCategory.CONSULTATION, page = 1)) },
                    label = { Text("Completed Consultations") },
                    modifier = Modifier.testTag("patient_filter_type_consultations")
                )
                FilterChip(
                    selected = criteria.recordCategoryFilter == RecordCategory.APPOINTMENT_TOKEN,
                    onClick = { viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = RecordCategory.APPOINTMENT_TOKEN, page = 1)) },
                    label = { Text("Appointments / Tokens") },
                    modifier = Modifier.testTag("patient_filter_type_appointments")
                )
                FilterChip(
                    selected = criteria.hasPrescriptionFilter == true,
                    onClick = {
                        val next = if (criteria.hasPrescriptionFilter == true) null else true
                        viewModel.updateHistoryCriteria(criteria.copy(hasPrescriptionFilter = next, page = 1))
                    },
                    label = { Text("With Prescription") },
                    modifier = Modifier.testTag("patient_filter_has_rx")
                )
            }

            if (filteredResult.records.isEmpty() && uiState.pastVisits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("patient_history_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No historical consultations or appointments match the selected filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.resetHistoryFilters() }) {
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("visit_history_list")
                ) {
                    items(filteredResult.records, key = { it.id }) { record ->
                        PatientConsultationHistoryCard(
                            record = record,
                            onClick = { viewModel.openHistoryDetail(record.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientConsultationHistoryCard(
    record: ConsultationRecord,
    onClick: () -> Unit
) {
    val isConsultation = record.recordCategory == RecordCategory.CONSULTATION
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("visit_card_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConsultation) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isConsultation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = if (isConsultation) "CONSULTATION" else "APPOINTMENT / TOKEN",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (record.isArchived) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "ARCHIVED (>3M)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                StatusBadge(status = record.appointmentStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${record.doctorName} • ${record.doctorSpecialty}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${record.clinicName} (${record.chamber}) • Token ${record.tokenLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Date: ${record.consultationDate} at ${record.consultationTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (record.diagnosis != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Diagnosis: ${record.diagnosis}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            if (record.hasPrescription && record.prescriptionSummary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rx: ${record.prescriptionSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (record.cancellationOrRejectionReason != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${record.cancellationOrRejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
