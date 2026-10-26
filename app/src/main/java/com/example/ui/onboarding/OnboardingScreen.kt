package com.example.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.di.ServiceLocator
import com.example.data.model.Clinic
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.domain.model.Role
import com.example.domain.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: (UserRole, String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { ServiceLocator.sehatFlowRepository }
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    var selectedRole by remember { mutableStateOf<UserRole?>(null) }
    var showRoadmapModal by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Clinic Admin Fields
    var clinicName by remember { mutableStateOf("") }
    var clinicAddress by remember { mutableStateOf("") }
    var clinicPhone by remember { mutableStateOf("") }

    // Doctor Fields
    var doctorName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var doctorSpecialty by remember { mutableStateOf("General Physician") }
    var doctorRoom by remember { mutableStateOf("OPD Cabin 1") }
    var doctorInviteCode by remember { mutableStateOf("") }

    // Receptionist Fields
    var receptionistName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var receptionistDesk by remember { mutableStateOf("Main Desk 1") }
    var receptionistInviteCode by remember { mutableStateOf("") }

    // Patient Fields
    var patientName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var patientPhone by remember { mutableStateOf(currentUser?.phoneNumber ?: "") }
    var patientAge by remember { mutableStateOf("28") }
    var patientClinicId by remember { mutableStateOf("clinic_demo") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_sehatflow_symbol),
                            contentDescription = "SehatFlow Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(
                                text = "Welcome to SehatFlow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Less Waiting. Smarter Care.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showRoadmapModal = true },
                        modifier = Modifier.testTag("btn_onboarding_roadmap")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Onboarding Roadmap",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Roadmap")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card with Official Uploaded SehatFlow Logo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sehatflow_symbol),
                        contentDescription = "SehatFlow Official Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("onboarding_header_logo")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Your Role",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Set up your clinic profile or join an existing queue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Role selection cards
            RoleSelectionCard(
                title = "Clinic Owner / Administrator",
                subtitle = "Register clinic, get doctor & receptionist codes, 14-day free trial (₹499/mo plan)",
                icon = Icons.Default.Business,
                isSelected = selectedRole == UserRole.CLINIC_ADMIN,
                badge = "14-Day Free Trial",
                onClick = { selectedRole = UserRole.CLINIC_ADMIN },
                testTag = "role_clinic_admin"
            )

            RoleSelectionCard(
                title = "Consulting Doctor",
                subtitle = "Call next patients, digital consultation, sound alerts & patient history",
                icon = Icons.Default.MedicalServices,
                isSelected = selectedRole == UserRole.DOCTOR,
                badge = null,
                onClick = { selectedRole = UserRole.DOCTOR },
                testTag = "role_doctor"
            )

            RoleSelectionCard(
                title = "Clinic Receptionist",
                subtitle = "Issue walk-in tokens, verify prescriptions, manage emergency priorities",
                icon = Icons.Default.SupportAgent,
                isSelected = selectedRole == UserRole.RECEPTIONIST,
                badge = null,
                onClick = { selectedRole = UserRole.RECEPTIONIST },
                testTag = "role_receptionist"
            )

            RoleSelectionCard(
                title = "Patient (Walk-in / Online)",
                subtitle = "Track live token, view doctors ahead, estimated wait time, no physical queue",
                icon = Icons.Default.Person,
                isSelected = selectedRole == UserRole.PATIENT,
                badge = "Zero App Sign-Up Fee",
                onClick = { selectedRole = UserRole.PATIENT },
                testTag = "role_patient"
            )

            // Dynamic Form based on selected role
            AnimatedVisibility(visible = selectedRole != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = when (selectedRole) {
                                UserRole.CLINIC_ADMIN -> "Clinic Details & Setup"
                                UserRole.DOCTOR -> "Doctor Profile"
                                UserRole.RECEPTIONIST -> "Reception Desk Setup"
                                UserRole.PATIENT -> "Patient Details"
                                null -> ""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        when (selectedRole) {
                            UserRole.CLINIC_ADMIN -> {
                                OutlinedTextField(
                                    value = clinicName,
                                    onValueChange = { clinicName = it },
                                    label = { Text("Clinic / Hospital Name") },
                                    placeholder = { Text("e.g. CareWell PolyClinic") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_clinic_name")
                                )
                                OutlinedTextField(
                                    value = clinicAddress,
                                    onValueChange = { clinicAddress = it },
                                    label = { Text("Clinic Address / City") },
                                    placeholder = { Text("e.g. Sector 12, Dwarka, New Delhi") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_clinic_address")
                                )
                                OutlinedTextField(
                                    value = clinicPhone,
                                    onValueChange = { clinicPhone = it },
                                    label = { Text("Official Contact Number") },
                                    placeholder = { Text("+91 98765 43210") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_clinic_phone")
                                )
                            }
                            UserRole.DOCTOR -> {
                                OutlinedTextField(
                                    value = doctorName,
                                    onValueChange = { doctorName = it },
                                    label = { Text("Doctor's Full Name") },
                                    placeholder = { Text("Dr. Rahul Sharma") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_doctor_name")
                                )
                                OutlinedTextField(
                                    value = doctorSpecialty,
                                    onValueChange = { doctorSpecialty = it },
                                    label = { Text("Medical Specialty") },
                                    placeholder = { Text("General Physician / Pediatrician") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_doctor_specialty")
                                )
                                OutlinedTextField(
                                    value = doctorRoom,
                                    onValueChange = { doctorRoom = it },
                                    label = { Text("Room / Cabin Number") },
                                    placeholder = { Text("Cabin 102") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_doctor_room")
                                )
                                OutlinedTextField(
                                    value = doctorInviteCode,
                                    onValueChange = { doctorInviteCode = it },
                                    label = { Text("Clinic Invite Code (optional for demo)") },
                                    placeholder = { Text("e.g. DOC-4819") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_doctor_invite")
                                )
                            }
                            UserRole.RECEPTIONIST -> {
                                OutlinedTextField(
                                    value = receptionistName,
                                    onValueChange = { receptionistName = it },
                                    label = { Text("Receptionist Name") },
                                    placeholder = { Text("Pooja Verma") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_receptionist_name")
                                )
                                OutlinedTextField(
                                    value = receptionistDesk,
                                    onValueChange = { receptionistDesk = it },
                                    label = { Text("Desk Name / Number") },
                                    placeholder = { Text("Main OPD Counter") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_receptionist_desk")
                                )
                                OutlinedTextField(
                                    value = receptionistInviteCode,
                                    onValueChange = { receptionistInviteCode = it },
                                    label = { Text("Clinic Invite Code (optional for demo)") },
                                    placeholder = { Text("e.g. REC-9321") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_receptionist_invite")
                                )
                            }
                            UserRole.PATIENT -> {
                                OutlinedTextField(
                                    value = patientName,
                                    onValueChange = { patientName = it },
                                    label = { Text("Patient Full Name") },
                                    placeholder = { Text("Aakash Kumar") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_patient_name")
                                )
                                OutlinedTextField(
                                    value = patientPhone,
                                    onValueChange = { patientPhone = it },
                                    label = { Text("Mobile / WhatsApp Number (Optional)") },
                                    placeholder = { Text("9876543210") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_patient_phone")
                                )
                                Text(
                                    text = "Health profile details (blood group, allergies, medications) are optional and can be added after setup or skipped.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            null -> {}
                        }

                        Button(
                            onClick = {
                                val role = selectedRole ?: return@Button
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        val uid = currentUser?.uid ?: "user_${System.currentTimeMillis()}"
                                        val email = currentUser?.email ?: "${role.name.lowercase()}@sehatflow.app"

                                        var verifiedInviteClinicId: String? = null
                                        if (role == UserRole.DOCTOR && doctorInviteCode.isNotBlank()) {
                                            val inviteRes = com.example.domain.model.StaffInvitationValidator.validateAndRedeemInvitation(
                                                userId = uid,
                                                rawInviteCode = doctorInviteCode,
                                                requestedRole = UserRole.DOCTOR
                                            )
                                            if (!inviteRes.isSuccess) {
                                                isLoading = false
                                                errorMessage = inviteRes.message
                                                return@launch
                                            }
                                            verifiedInviteClinicId = inviteRes.clinicId
                                        } else if (role == UserRole.RECEPTIONIST && receptionistInviteCode.isNotBlank()) {
                                            val inviteRes = com.example.domain.model.StaffInvitationValidator.validateAndRedeemInvitation(
                                                userId = uid,
                                                rawInviteCode = receptionistInviteCode,
                                                requestedRole = UserRole.RECEPTIONIST
                                            )
                                            if (!inviteRes.isSuccess) {
                                                isLoading = false
                                                errorMessage = inviteRes.message
                                                return@launch
                                            }
                                            verifiedInviteClinicId = inviteRes.clinicId
                                        }

                                        val targetClinicId = when (role) {
                                            UserRole.CLINIC_ADMIN -> "clinic_${System.currentTimeMillis()}"
                                            UserRole.DOCTOR -> verifiedInviteClinicId ?: "clinic_demo"
                                            UserRole.RECEPTIONIST -> verifiedInviteClinicId ?: "clinic_demo"
                                            UserRole.PATIENT -> patientClinicId.ifBlank { "clinic_demo" }
                                        }

                                        if (role == UserRole.CLINIC_ADMIN) {
                                            val cName = clinicName.ifBlank { "Sehat Care Center" }
                                            val randomDocCode = com.example.core.util.SecurityTokenGenerator.generateInviteCode("DOC")
                                            val randomRecCode = com.example.core.util.SecurityTokenGenerator.generateInviteCode("REC")

                                            com.example.domain.model.StaffInvitationValidator.registerClinicInvitations(
                                                clinicId = targetClinicId,
                                                clinicName = cName,
                                                adminUserId = uid,
                                                doctorInviteCode = randomDocCode,
                                                receptionistInviteCode = randomRecCode
                                            )

                                            val clinic = Clinic(
                                                clinicId = targetClinicId,
                                                name = cName,
                                                address = clinicAddress.ifBlank { "Medical Hub" },
                                                phone = clinicPhone.ifBlank { "+91 9876543210" },
                                                adminUid = uid,
                                                inviteCodeDoctor = randomDocCode,
                                                inviteCodeReceptionist = randomRecCode,
                                                subscriptionStatus = SubscriptionStatus.TRIAL.name,
                                                monthlyFee = 499L
                                            )
                                            if (currentUser != null) {
                                                repo.saveClinic(clinic)
                                            }
                                        }

                                        if (role == UserRole.PATIENT) {
                                            com.example.domain.model.PatientIdentityRegistry.resolveOrRegisterPatient(
                                                authenticatedPatientId = uid,
                                                fullName = patientName.ifBlank { "OPD Patient" },
                                                phoneNumber = patientPhone.ifBlank { null },
                                                clinicId = targetClinicId
                                            )
                                        }

                                        val profile = UserProfile(
                                            userId = uid,
                                            email = email,
                                            displayName = when (role) {
                                                UserRole.CLINIC_ADMIN -> clinicName.ifBlank { "Clinic Administrator" }
                                                UserRole.DOCTOR -> doctorName.ifBlank { "Dr. Aisha Khan" }
                                                UserRole.RECEPTIONIST -> receptionistName.ifBlank { "Receptionist" }
                                                UserRole.PATIENT -> patientName.ifBlank { "OPD Patient" }
                                            },
                                            role = role.name,
                                            clinicId = targetClinicId,
                                            phone = when (role) {
                                                UserRole.CLINIC_ADMIN -> clinicPhone
                                                UserRole.DOCTOR -> ""
                                                UserRole.RECEPTIONIST -> ""
                                                UserRole.PATIENT -> patientPhone
                                            },
                                            specialization = if (role == UserRole.DOCTOR) doctorSpecialty else "",
                                            roomNumber = if (role == UserRole.DOCTOR) doctorRoom else "",
                                            deskName = if (role == UserRole.RECEPTIONIST) receptionistDesk else ""
                                        )

                                        if (currentUser != null) {
                                            repo.saveUserProfile(profile)
                                        }

                                        // Update session preferences in ServiceLocator
                                        val appRole = when (role) {
                                            UserRole.CLINIC_ADMIN -> Role.OWNER
                                            UserRole.DOCTOR -> Role.DOCTOR
                                            UserRole.RECEPTIONIST -> Role.RECEPTIONIST
                                            UserRole.PATIENT -> Role.PATIENT
                                        }
                                        ServiceLocator.sessionPreferences.saveSession(
                                            UserSession(
                                                token = "jwt_token_$uid",
                                                userId = uid,
                                                displayName = profile.displayName,
                                                role = appRole,
                                                clinicId = targetClinicId,
                                                doctorId = if (role == UserRole.DOCTOR) uid else null
                                            )
                                        )

                                        isLoading = false
                                        onOnboardingComplete(role, targetClinicId)
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = e.localizedMessage ?: "Failed to complete onboarding"
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_complete_onboarding"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Complete Setup & Continue", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRoadmapModal) {
        OnboardingRoadmapDialog(onDismiss = { showRoadmapModal = false })
    }
}

@Composable
fun RoleSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

@Composable
fun OnboardingRoadmapDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "SehatFlow Clinic Onboarding Roadmap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RoadmapStepItem(
                    stepNumber = "1",
                    title = "Clinic Registration & Instant 14-Day Pro Trial",
                    description = "Clinic Admin registers basic clinic details. Instantly unlocks all SehatFlow Pro capabilities under a zero-cost 14-day trial."
                )

                RoadmapStepItem(
                    stepNumber = "2",
                    title = "Doctor & Receptionist Staff Delegation",
                    description = "Unique 6-character invite codes (e.g. DOC-4819, REC-9321) are auto-generated. Staff simply download SehatFlow, enter their code, and are linked to their OPD desk."
                )

                RoadmapStepItem(
                    stepNumber = "3",
                    title = "Digital Token Issuance & DPDP Consent",
                    description = "Receptionist issues walk-in tokens or reviews digital prescriptions uploaded by patients under Digital Personal Data Protection Act compliance."
                )

                RoadmapStepItem(
                    stepNumber = "4",
                    title = "Doctor Consultation Flow & Audio Chime",
                    description = "Consulting doctors view live waiting queues, tap 'Call Next', 'Start Consultation', and 'Complete' with automated sound chimes and status sync."
                )

                RoadmapStepItem(
                    stepNumber = "5",
                    title = "Monthly Subscription at ₹499/Clinic",
                    description = "Flat ₹499/month per clinic covers unlimited doctor cabins, reception desks, and patient tokens with cloud backup and zero patient fees."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}

@Composable
fun RoadmapStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
