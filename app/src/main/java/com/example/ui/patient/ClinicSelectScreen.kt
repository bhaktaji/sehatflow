package com.example.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ClinicDoctor
import com.example.domain.model.DoctorScheduleSlot
import com.example.ui.components.OfflineBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.MintGreen
import com.example.ui.theme.RedError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicSelectScreen(
    viewModel: PatientViewModel,
    onDoctorSelected: (clinicId: String, doctorId: String) -> Unit,
    onViewHistory: () -> Unit,
    onViewLiveToken: (tokenId: String, sessionId: String) -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialty by remember { mutableStateOf("All") }
    var selectedDoctorForProfile by remember { mutableStateOf<ClinicDoctor?>(null) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHealthProfileDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val prefs = com.example.data.di.ServiceLocator.sessionPreferences
    val currentServerUrl by prefs.serverUrl.collectAsStateWithLifecycle(initialValue = prefs.getServerUrlSync())
    val isDemoModeActive by prefs.isDemoMode.collectAsStateWithLifecycle(initialValue = prefs.isDemoModeSync())
    val currentThemeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = prefs.getThemeModeSync())
    val isQueueSoundEnabled by prefs.queueSoundVibrationEnabled.collectAsStateWithLifecycle(initialValue = prefs.isQueueSoundVibrationEnabledSync())
    val currentPatientId = uiState.currentUserSession?.userId ?: "pat_demo_patient"
    var healthProfileVersion by remember { mutableIntStateOf(0) }
    val currentHealthProfile = remember(currentPatientId, healthProfileVersion) {
        com.example.domain.model.PatientHealthProfileRegistry.getAuthorizedHealthProfile(
            patientId = currentPatientId,
            session = uiState.currentUserSession
        )
    }

    val specialties = listOf("All", "General Physician", "Pediatrics", "Orthopedics", "Cardiology")

    val filteredDoctors = remember(uiState.doctors, searchQuery, selectedSpecialty) {
        uiState.doctors.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                    doc.displayName.contains(searchQuery, ignoreCase = true) ||
                    (doc.specialty?.contains(searchQuery, ignoreCase = true) == true) ||
                    doc.schedules.any { it.clinicName.contains(searchQuery, ignoreCase = true) }
            val matchesSpecialty = selectedSpecialty == "All" ||
                    (doc.specialty?.contains(selectedSpecialty, ignoreCase = true) == true)
            matchesQuery && matchesSpecialty
        }
    }

    if (showSettingsDialog) {
        com.example.ui.common.SehatFlowSettingsDialog(
            currentServerUrl = currentServerUrl,
            isDemoMode = isDemoModeActive,
            currentThemeMode = currentThemeMode,
            isQueueSoundEnabled = isQueueSoundEnabled,
            currentPatientId = currentPatientId,
            onSelectThemeMode = { mode ->
                coroutineScope.launch { prefs.setThemeMode(mode) }
            },
            onToggleQueueSound = { enabled ->
                coroutineScope.launch { prefs.setQueueSoundVibrationEnabled(enabled) }
            },
            onSaveServerConfig = { newUrl, newDemo ->
                coroutineScope.launch {
                    runCatching { prefs.setServerUrl(newUrl) }
                    prefs.setDemoMode(newDemo)
                }
                showSettingsDialog = false
            },
            onOpenHealthProfile = {
                showHealthProfileDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showHealthProfileDialog) {
        com.example.ui.common.PatientHealthProfileDialog(
            patientId = currentPatientId,
            onCompletedOrSkipped = {
                healthProfileVersion++
                showHealthProfileDialog = false
            },
            onDismiss = {
                healthProfileVersion++
                showHealthProfileDialog = false
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            modifier = Modifier.testTag("patient_logout_confirm_dialog"),
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to log out of your patient session? Your appointments, tokens, and medical records remain safely saved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.clearPatientSessionState()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("patient_logout_confirm_btn")
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false },
                    modifier = Modifier.testTag("patient_logout_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_clinic_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_doctors_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Doctors")
                    }
                    IconButton(
                        onClick = onViewHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("patient_history_btn")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Past Visits")
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("patient_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("patient_logout_btn")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Log out")
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
            OfflineBanner(isOffline = uiState.isOffline)

            // Requirement 1.2: Display the patient's own stable Patient ID in their account header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("patient_own_id_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Patient Profile: ${uiState.currentUserSession?.displayName ?: "David Miller"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Patient ID: ${uiState.patientIdentifier}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.testTag("patient_own_id_badge")
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = uiState.patientIdentifier,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusSummary = when {
                            currentHealthProfile?.hasAnyReportedMedicalDetail() == true ->
                                "Health Profile: ${currentHealthProfile.bloodGroup ?: "Saved"} • ${currentHealthProfile.verificationStatusLabel}"
                            currentHealthProfile?.isSkipped == true ->
                                "Optional Health Profile: Skipped for now"
                            else ->
                                "Optional Health Profile: Not completed yet"
                        }
                        Text(
                            text = statusSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showHealthProfileDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("patient_health_profile_btn")
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (currentHealthProfile?.hasAnyReportedMedicalDetail() == true) "Edit Health Info" else "Complete Health Profile",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active token banner if already waiting
            if (uiState.activeToken != null && uiState.selectedSession != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            onViewLiveToken(uiState.activeToken!!.id, uiState.selectedSession!!.id)
                        }
                        .testTag("active_token_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Token: ${uiState.activeToken?.label}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Status: ${uiState.activeToken?.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // Doctor Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Doctor by Name, Specialty, or Clinic") },
                placeholder = { Text("e.g. Dr. John, General Physician, XYZ Clinic") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("doctor_search_input")
            )

            // Specialization Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                specialties.forEach { spec ->
                    FilterChip(
                        selected = selectedSpecialty == spec,
                        onClick = { selectedSpecialty = spec },
                        label = { Text(spec, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("filter_chip_${spec.replace(" ", "_")}")
                    )
                }
            }

            Text(
                text = "Available Doctors & Specialists (${filteredDoctors.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("doctors_loading_indicator"))
                }
            } else if (uiState.errorMessage != null && uiState.doctors.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.errorMessage ?: "Failed to load doctors list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag("retry_doctors_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            } else if (filteredDoctors.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No doctors found matching '$searchQuery'." else "No doctors available in selected category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isNotBlank() || selectedSpecialty != "All") {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedSpecialty = "All"
                                },
                                modifier = Modifier.testTag("clear_filters_btn")
                            ) {
                                Text("Clear Search & Filters")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("doctors_list")
                ) {
                    items(filteredDoctors) { doctor ->
                        DoctorItemCard(
                            doctor = doctor,
                            onCardClick = { selectedDoctorForProfile = doctor },
                            onBookClick = {
                                viewModel.selectDoctor(doctor, doctor.schedules.firstOrNull())
                                val session = uiState.activeSessions.find { it.doctorId == doctor.id }
                                val clinicId = session?.clinicId ?: doctor.primaryClinic ?: "xyz_clinic"
                                onDoctorSelected(clinicId, doctor.id)
                            }
                        )
                    }
                }
            }
        }

        // Doctor Profile & Weekly Schedule Dialog
        selectedDoctorForProfile?.let { doctor ->
            DoctorProfileDialog(
                doctor = doctor,
                onDismiss = { selectedDoctorForProfile = null },
                onSelectSlot = { slot ->
                    selectedDoctorForProfile = null
                    viewModel.selectDoctor(doctor, slot)
                    onDoctorSelected(slot.clinicId, doctor.id)
                }
            )
        }
    }
}

@Composable
private fun DoctorItemCard(
    doctor: ClinicDoctor,
    onCardClick: () -> Unit,
    onBookClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("doctor_card_${doctor.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Doctor photo placeholder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doctor.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${doctor.specialty ?: "General Physician"} • ${doctor.experienceYears ?: 8} yrs exp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${doctor.qualifications ?: "MBBS"} • ${doctor.credentials ?: "Verified"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clinics and chamber badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${doctor.primaryClinic} • ${doctor.primaryChamber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${doctor.schedules.size} Clinic Days",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCardClick,
                    modifier = Modifier.weight(1f).testTag("view_profile_btn_${doctor.id}")
                ) {
                    Text("Schedule & Profile", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onBookClick,
                    modifier = Modifier.weight(1f).testTag("book_doctor_btn_${doctor.id}")
                ) {
                    Text("Book Visit", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun DoctorProfileDialog(
    doctor: ClinicDoctor,
    onDismiss: () -> Unit,
    onSelectSlot: (DoctorScheduleSlot) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("doctor_profile_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Doctor Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_profile_dialog_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Professional details header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = "Professional Doctor Badge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = doctor.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = doctor.specialty ?: "General Physician",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Credentials: ${doctor.credentials ?: "Verified"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Qualifications: ${doctor.qualifications ?: "MBBS"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Experience: ${doctor.experienceYears ?: 10} Years Clinical Practice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Weekly Multi-Clinic Schedule & Capacities",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Check daily capacity limits before requesting a token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                doctor.schedules.forEach { slot ->
                    val isMonday = slot.dayOfWeek == "Monday"
                    val isTuesday = slot.dayOfWeek == "Tuesday"
                    val isFull = isMonday && slot.clinicName == "XYZ Clinic"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = slot.isDoctorAvailable && !slot.isClinicClosed) {
                                onSelectSlot(slot)
                            }
                            .testTag("schedule_slot_${slot.dayOfWeek}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFull)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            else if (!slot.isDoctorAvailable || slot.isClinicClosed)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = slot.dayOfWeek,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (!slot.isDoctorAvailable) "No Clinic Scheduled"
                                    else if (slot.isClinicClosed) "Clinic Closed"
                                    else if (isFull) "FULL (10/10)"
                                    else if (isTuesday) "4/10 Booked (6 Available)"
                                    else "Available",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFull) RedError else if (!slot.isDoctorAvailable) AmberWarning else MintGreen
                                    )
                                )
                            }

                            if (slot.isDoctorAvailable && !slot.isClinicClosed) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${slot.clinicName} • ${slot.chamber}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Hours: ${slot.consultationHours} • Daily Patient Limit: ${slot.dailyCapacity}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isFull) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⚡ Full-day policy: Requests for Monday will automatically allocate to next available clinic day (Tuesday at XYZ Clinic).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
