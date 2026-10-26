package com.example.ui.doctor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorConsultationHistoryScreen(
    viewModel: DoctorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val criteria = uiState.historyCriteria
    val filteredResult = uiState.filteredHistory
    var addendumInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshDoctorHistory()
    }

    val selectedRecord = uiState.selectedHistoryRecord
    if (selectedRecord != null) {
        AlertDialog(
            onDismissRequest = {
                addendumInput = ""
                viewModel.dismissConsultationDetail()
            },
            modifier = Modifier.testTag("doctor_consultation_detail_dialog"),
            title = {
                Column {
                    Text(
                        text = if (selectedRecord.recordCategory == RecordCategory.CONSULTATION) {
                            "Clinical Consultation • ${selectedRecord.tokenLabel}"
                        } else {
                            "Appointment Record • ${selectedRecord.tokenLabel}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Patient: ${selectedRecord.patientName} (${selectedRecord.patientIdentifier}) • v${selectedRecord.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Date & Time: ${selectedRecord.consultationDate} at ${selectedRecord.consultationTime}", fontWeight = FontWeight.Medium)
                    Text("Historical Location: ${selectedRecord.clinicName} • ${selectedRecord.chamber}")
                    Text("Consultation Status: ${selectedRecord.consultationStatus}")
                    Text("Follow-up Status: ${selectedRecord.followUpStatus}")

                    HorizontalDivider()

                    Text(
                        text = "Presenting Complaint: ${selectedRecord.presentingComplaint ?: "Not recorded"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Allergies / Medical History: ${selectedRecord.allergiesOrMedicalHistory ?: "Not recorded"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (selectedRecord.diagnosis != null) {
                        Text("Recorded Diagnosis:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(selectedRecord.diagnosis, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Diagnosis: Not recorded (${selectedRecord.consultationStatus})", style = MaterialTheme.typography.bodySmall)
                    }

                    if (selectedRecord.clinicalNotes != null) {
                        Text("Clinical Notes:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(selectedRecord.clinicalNotes, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Clinical Notes: Not recorded", style = MaterialTheme.typography.bodySmall)
                    }

                    if (selectedRecord.prescriptionSummary != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Prescription (${selectedRecord.prescriptionId ?: "Verified"})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text(selectedRecord.prescriptionSummary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Text(
                        text = "Follow-up Plan: ${selectedRecord.followUpRecommendations ?: "Not recorded"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (selectedRecord.lastUpdatedNote != null) {
                        Text(
                            text = "Audit: ${selectedRecord.lastUpdatedNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Authorized Clinical Addendum / Correction Workflow (Preserves original record)
                    if (selectedRecord.recordCategory == RecordCategory.CONSULTATION) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Append Versioned Clinical Addendum",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = addendumInput,
                            onValueChange = { addendumInput = it },
                            label = { Text("Clinical correction or follow-up addendum") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("doctor_addendum_input"),
                            singleLine = false,
                            maxLines = 3
                        )
                        Button(
                            onClick = {
                                if (addendumInput.isNotBlank()) {
                                    viewModel.addClinicalAddendum(selectedRecord.id, addendumInput)
                                    addendumInput = ""
                                }
                            },
                            enabled = addendumInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("doctor_save_addendum_btn")
                        ) {
                            Text("Save Addendum (Preserves Original Record)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addendumInput = ""
                        viewModel.dismissConsultationDetail()
                    },
                    modifier = Modifier.testTag("close_doctor_consultation_detail_btn")
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
                        Text("Consultation History", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Active Window: ${filteredResult.activeWindowLabel} (${filteredResult.totalMatchedCount} records)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("doctor_history_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.resetHistoryFilters() },
                        modifier = Modifier.testTag("doctor_clear_history_filters_btn")
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
            // Search by Patient Name, Patient ID, Diagnosis, or Token
            OutlinedTextField(
                value = criteria.searchQuery,
                onValueChange = {
                    viewModel.updateHistoryCriteria(criteria.copy(searchQuery = it, page = 1))
                },
                label = { Text("Search Patient Name, ID (e.g. PAT-1001), or Diagnosis") },
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
                    .testTag("doctor_history_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // 1. Date Range Window Chips (Default: Last 3 Months; 2-Year Eligible Window; All Time / Archived)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HistoryDateRangePreset.LAST_3_MONTHS,
                    HistoryDateRangePreset.LAST_30_DAYS,
                    HistoryDateRangePreset.LAST_2_YEARS,
                    HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
                ).forEach { preset ->
                    FilterChip(
                        selected = criteria.dateRangePreset == preset,
                        onClick = {
                            viewModel.updateHistoryCriteria(criteria.copy(dateRangePreset = preset, page = 1))
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.testTag("doctor_filter_date_${preset.name}")
                    )
                }
            }

            // 2. Clinic & Chamber Filter Chips (Supports Multi-Clinic Doctors like Dr. John)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All Clinics", "XYZ Clinic" to "XYZ Clinic", "ABC Clinic" to "ABC Clinic").forEach { (clinicKey, label) ->
                    FilterChip(
                        selected = criteria.clinicIdFilter == clinicKey,
                        onClick = {
                            viewModel.updateHistoryCriteria(criteria.copy(clinicIdFilter = clinicKey, page = 1))
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("doctor_filter_clinic_$clinicKey")
                    )
                }

                listOf("ALL" to "All Chambers", "Chamber A" to "Chamber A", "Chamber B" to "Chamber B").forEach { (chamberKey, label) ->
                    FilterChip(
                        selected = criteria.chamberFilter == chamberKey,
                        onClick = {
                            viewModel.updateHistoryCriteria(criteria.copy(chamberFilter = chamberKey, page = 1))
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("doctor_filter_chamber_$chamberKey")
                    )
                }
            }

            // 3. Record Type, Prescription, and Follow-Up Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = criteria.recordCategoryFilter == null,
                    onClick = {
                        viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = null, page = 1))
                    },
                    label = { Text("All Records") },
                    modifier = Modifier.testTag("doctor_filter_category_ALL")
                )
                FilterChip(
                    selected = criteria.recordCategoryFilter == RecordCategory.CONSULTATION,
                    onClick = {
                        viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = RecordCategory.CONSULTATION, page = 1))
                    },
                    label = { Text("Completed Consultations") },
                    modifier = Modifier.testTag("doctor_filter_category_CONSULTATION")
                )
                FilterChip(
                    selected = criteria.recordCategoryFilter == RecordCategory.APPOINTMENT_TOKEN,
                    onClick = {
                        viewModel.updateHistoryCriteria(criteria.copy(recordCategoryFilter = RecordCategory.APPOINTMENT_TOKEN, page = 1))
                    },
                    label = { Text("Cancellations / No-Shows") },
                    modifier = Modifier.testTag("doctor_filter_category_APPOINTMENT_TOKEN")
                )
                FilterChip(
                    selected = criteria.hasPrescriptionFilter == true,
                    onClick = {
                        val next = if (criteria.hasPrescriptionFilter == true) null else true
                        viewModel.updateHistoryCriteria(criteria.copy(hasPrescriptionFilter = next, page = 1))
                    },
                    label = { Text("Has Prescription") },
                    modifier = Modifier.testTag("doctor_filter_has_rx")
                )
            }

            if (filteredResult.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("doctor_history_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MedicalInformation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No consultations match the selected clinic, chamber, or date filters.",
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
                        .testTag("doctor_consultation_history_list")
                ) {
                    items(filteredResult.records, key = { it.id }) { record ->
                        DoctorConsultationCard(
                            record = record,
                            onClick = { viewModel.openConsultationDetail(record.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorConsultationCard(
    record: ConsultationRecord,
    onClick: () -> Unit
) {
    val isConsultation = record.recordCategory == RecordCategory.CONSULTATION
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("doctor_history_card_${record.id}"),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isConsultation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (isConsultation) "CONSULTATION" else "NON-CONSULT (${record.appointmentStatus.name})",
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
                text = "${record.patientName} • ${record.patientIdentifier}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Location: ${record.clinicName} • ${record.chamber} (Token ${record.tokenLabel})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Date & Time: ${record.consultationDate} at ${record.consultationTime}",
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

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (record.hasPrescription) "Rx: Issued" else "Rx: None",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.hasPrescription) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Follow-up: ${record.followUpStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
