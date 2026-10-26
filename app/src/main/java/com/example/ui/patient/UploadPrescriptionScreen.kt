package com.example.ui.patient

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.DataRetentionPolicyEngine
import com.example.ui.components.DpdpConsentDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPrescriptionScreen(
    viewModel: PatientViewModel,
    clinicId: String,
    doctorId: String,
    onBack: () -> Unit,
    onTokenRequested: (tokenId: String, sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var patientName by remember { mutableStateOf("") }
    var presentingComplaint by remember { mutableStateOf("") }
    var needsDoctorAssignment by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var consentGiven by remember { mutableStateOf(false) }

    val hasEmergencyIndicator = remember(presentingComplaint) {
        DataRetentionPolicyEngine.containsPotentialEmergencyIndicator(presentingComplaint)
    }

    // Android zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Generic Document Picker (for PDF prescriptions)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_prescription_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Appointment details card
            val doctor = uiState.selectedDoctor
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("appointment_summary_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val slot = uiState.selectedSlot
                    val clinicName = slot?.clinicName ?: doctor?.primaryClinic ?: "XYZ Clinic"
                    val chamber = slot?.chamber ?: doctor?.primaryChamber ?: "Chamber A"
                    val dayOfWeek = slot?.dayOfWeek ?: "Monday"
                    val capacity = slot?.dailyCapacity ?: 10

                    Text(
                        text = if (needsDoctorAssignment) {
                            "Booking: Receptionist Doctor Assignment ($clinicName)"
                        } else {
                            "Booking with: ${doctor?.displayName ?: "Dr. John"}"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Patient ID: ${uiState.patientIdentifier} • $clinicName • $chamber • $dayOfWeek (Max $capacity patients/day)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (dayOfWeek == "Monday" && clinicName == "XYZ Clinic") {
                            "⚡ Triage Policy: If Monday is at full capacity (10/10), verified tokens automatically allocate to Tuesday at XYZ Clinic."
                        } else {
                            "⚡ Authoritative Schedule: Session at $clinicName, $chamber with strict daily cap of $capacity patients."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Requirement 5.1 & 5.2: Presenting Complaint Field (Required when booking without a prescription)
            OutlinedTextField(
                value = presentingComplaint,
                onValueChange = { if (it.length <= DataRetentionPolicyEngine.MAX_COMPLAINT_LENGTH) presentingComplaint = it },
                label = { Text("What problem are you experiencing? (Presenting Complaint)") },
                placeholder = { Text("e.g. Fever and throat pain for 3 days, or knee pain while walking") },
                supportingText = {
                    Text(
                        "Helps clinic staff route your appointment (${presentingComplaint.length}/${DataRetentionPolicyEngine.MAX_COMPLAINT_LENGTH}). Required if you do not have a prescription. Not a medical diagnosis."
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("presenting_complaint_input")
            )

            // Requirement 5.5: Non-diagnostic Emergency Caution Banner
            if (hasEmergencyIndicator) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("emergency_warning_banner")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency Caution",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = "Important: If you may be experiencing a medical emergency, please seek immediate emergency hospital care or call emergency services (108 / 112). This outpatient booking queue does not provide emergency triage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option to request receptionist doctor assignment based on problem
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { needsDoctorAssignment = !needsDoctorAssignment }
                    .padding(vertical = 4.dp)
                    .testTag("need_doctor_assignment_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = needsDoctorAssignment,
                    onCheckedChange = { needsDoctorAssignment = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Not sure which doctor to choose? Let the receptionist assign an available doctor based on my problem.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Guidelines info card (Prescription is now optional when complaint is provided)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Prescription upload is optional for new problems or returning visits without a prior prescription. Upload a photo/PDF below if available, or describe your problem above to proceed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prescription Preview Box / Upload Area (Optional)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("upload_dropzone"),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Prescription",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to attach prescription photo (Optional)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Max 5 MB • JPEG, PNG, PDF • Or skip if no prescription",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = { documentPickerLauncher.launch("application/pdf") },
                    modifier = Modifier.testTag("pdf_picker_btn")
                ) {
                    Text("Or choose PDF document")
                }
                if (selectedImageUri != null) {
                    TextButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier.testTag("clear_rx_attachment_btn")
                    ) {
                        Text("Remove Attachment")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = patientName,
                onValueChange = { patientName = it },
                label = { Text("Patient Name (Optional)") },
                placeholder = { Text("e.g. David Miller") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("patient_name_optional")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error feedback if validation fails
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("booking_error_text")
                )
            }

            // DPDP Act 2023 Consent row
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (consentGiven) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showConsentDialog = true }
                    .testTag("dpdp_consent_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (consentGiven) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (consentGiven) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (consentGiven) "DPDP Act 2023 Consent Granted ✓" else "Tap to read & accept DPDP Consent (Required)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (consentGiven) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canSubmit = consentGiven && !uiState.isUploading &&
                (selectedImageUri != null || presentingComplaint.trim().isNotEmpty())

            Button(
                onClick = {
                    viewModel.uploadPrescriptionAndRequestToken(
                        context = context,
                        fileUri = selectedImageUri,
                        displayName = patientName.ifBlank { null },
                        consentAccepted = consentGiven,
                        presentingComplaint = presentingComplaint.ifBlank { null },
                        needsDoctorAssignment = needsDoctorAssignment,
                        onSuccess = onTokenRequested
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_prescription_btn")
            ) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        if (selectedImageUri != null) {
                            stringResource(R.string.submit_rx_btn)
                        } else {
                            "Submit Appointment Request (No Rx)"
                        }
                    )
                }
            }
        }

        if (showConsentDialog) {
            DpdpConsentDialog(
                onConsentGiven = {
                    consentGiven = true
                    showConsentDialog = false
                },
                onDismiss = { showConsentDialog = false }
            )
        }
    }
}
