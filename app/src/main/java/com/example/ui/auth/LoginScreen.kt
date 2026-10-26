package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.di.ServiceLocator
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.domain.model.PatientIdentityRegistry
import com.example.domain.model.Role
import com.example.domain.model.StaffInvitationValidator
import com.example.domain.model.UserSession
import com.example.ui.common.PatientHealthProfileDialog
import com.example.ui.common.SehatFlowSettingsDialog
import com.example.ui.onboarding.OnboardingRoadmapDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentServerUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val isDemoModeActive by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isQueueSoundEnabled by viewModel.queueSoundVibrationEnabled.collectAsStateWithLifecycle()

    var showStaffSignInForm by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }
    var showRoadmapModal by remember { mutableStateOf(false) }
    var showHealthProfileModal by remember { mutableStateOf(false) }
    var pendingPatientSession by remember { mutableStateOf<UserSession?>(null) }

    var isGoogleSigningIn by remember { mutableStateOf(false) }
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    // Patient Form State
    var phoneNumber by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    // Staff Form State
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var staffInviteCode by remember { mutableStateOf("") }
    var staffInviteFeedback by remember { mutableStateOf<String?>(null) }
    var staffInviteIsError by remember { mutableStateOf(false) }

    if (showServerSettings) {
        SehatFlowSettingsDialog(
            currentServerUrl = currentServerUrl,
            isDemoMode = isDemoModeActive,
            currentThemeMode = currentThemeMode,
            isQueueSoundEnabled = isQueueSoundEnabled,
            currentPatientId = pendingPatientSession?.userId ?: "pat_demo_patient",
            onSelectThemeMode = { viewModel.setThemeMode(it) },
            onToggleQueueSound = { viewModel.setQueueSoundVibrationEnabled(it) },
            onSaveServerConfig = { newUrl, newDemo ->
                viewModel.updateServerUrl(newUrl)
                viewModel.setDemoMode(newDemo)
                showServerSettings = false
            },
            onOpenHealthProfile = {
                showHealthProfileModal = true
            },
            onDismiss = { showServerSettings = false }
        )
    }

    if (showHealthProfileModal) {
        val targetPatientId = pendingPatientSession?.userId ?: "pat_demo_patient"
        PatientHealthProfileDialog(
            patientId = targetPatientId,
            onCompletedOrSkipped = {
                showHealthProfileModal = false
                pendingPatientSession?.let { sessionToCommit ->
                    coroutineScope.launch {
                        ServiceLocator.sessionPreferences.saveSession(sessionToCommit)
                        pendingPatientSession = null
                    }
                }
            },
            onDismiss = {
                showHealthProfileModal = false
                pendingPatientSession?.let { sessionToCommit ->
                    coroutineScope.launch {
                        ServiceLocator.sessionPreferences.saveSession(sessionToCommit)
                        pendingPatientSession = null
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("login_top_bar"),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showServerSettings = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("server_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. BRANDING & OFFICIAL UPLOADED SEHATFLOW LOGO (Transparent, blended into page)
                val isDarkSurface =
                    (MaterialTheme.colorScheme.background.red * 0.299f +
                        MaterialTheme.colorScheme.background.green * 0.587f +
                        MaterialTheme.colorScheme.background.blue * 0.114f) < 0.5f
                val logoResId = if (isDarkSurface) {
                    R.drawable.ic_sehatflow_logo_dark
                } else {
                    R.drawable.ic_sehatflow_logo
                }
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "${stringResource(R.string.login_title)} — ${stringResource(R.string.login_subtitle)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(148.dp)
                        .testTag("welcome_app_logo")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("welcome_tagline")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.login_supporting_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("welcome_supporting_text")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Error banner if any
                if (uiState.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("error_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (uiState.errorMessage?.contains("Cannot reach", ignoreCase = true) == true ||
                                uiState.errorMessage?.contains("connect", ignoreCase = true) == true
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showServerSettings = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.setDemoMode(true)
                                            viewModel.clearError()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Use Demo Mode", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. INSTANT DEMO MODE CARD (Prominent Near Top for Customer Evaluation)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("demo_preview_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Instant Demo Mode",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "No Sign-In Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Test complete Patient, Doctor, Receptionist, and Clinic Admin workflows instantly:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.loginAsDemo(Role.PATIENT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_patient_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Patient", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.loginAsDemo(Role.DOCTOR) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_doctor_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Doctor", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.loginAsDemo(Role.RECEPTIONIST) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_reception_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Desk", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.loginAsDemo(Role.OWNER) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_owner_btn"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Admin", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 3. PRIMARY SECTION: FOR PATIENTS (Sections 4.1, 4.2, 5, 6, 7)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("google_auth_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.for_patients_header),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = { showHealthProfileModal = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("welcome_health_profile_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Health Profile (Optional)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Primary Patient Google Sign-In CTA
                        Button(
                            onClick = {
                                isGoogleSigningIn = true
                                googleSignInError = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isGoogleSigningIn = false
                                        val user = Firebase.auth.currentUser
                                        if (user != null) {
                                            coroutineScope.launch {
                                                val existingProfile = ServiceLocator.sehatFlowRepository.getUserProfile(user.uid)
                                                val resolvedName = existingProfile?.displayName?.ifBlank { null }
                                                    ?: user.displayName?.ifBlank { null }
                                                    ?: "Patient"
                                                // Ensure authoritative Patient ID without duplicate creation
                                                PatientIdentityRegistry.resolveOrRegisterPatient(
                                                    authenticatedPatientId = user.uid,
                                                    fullName = resolvedName,
                                                    phoneNumber = existingProfile?.phone?.ifBlank { null } ?: user.phoneNumber
                                                )
                                                if (existingProfile == null) {
                                                    // Save initial PATIENT profile in Firestore
                                                    val newPatientProfile = UserProfile(
                                                        userId = user.uid,
                                                        email = user.email ?: "",
                                                        displayName = resolvedName,
                                                        role = UserRole.PATIENT.name,
                                                        clinicId = "clinic_1",
                                                        phone = user.phoneNumber ?: ""
                                                    )
                                                    runCatching {
                                                        ServiceLocator.sehatFlowRepository.saveUserProfile(newPatientProfile)
                                                    }
                                                    val session = UserSession(
                                                        token = "jwt_${user.uid}",
                                                        userId = user.uid,
                                                        displayName = resolvedName,
                                                        role = Role.PATIENT,
                                                        clinicId = "clinic_1",
                                                        doctorId = null
                                                    )
                                                    // Offer optional Health Profile step with "Skip for now"
                                                    pendingPatientSession = session
                                                    showHealthProfileModal = true
                                                } else {
                                                    val appRole = when (existingProfile.role) {
                                                        "CLINIC_ADMIN" -> Role.OWNER
                                                        "DOCTOR" -> Role.DOCTOR
                                                        "RECEPTIONIST" -> Role.RECEPTIONIST
                                                        else -> Role.PATIENT
                                                    }
                                                    val session = UserSession(
                                                        token = "jwt_${user.uid}",
                                                        userId = user.uid,
                                                        displayName = resolvedName,
                                                        role = appRole,
                                                        clinicId = existingProfile.clinicId.ifBlank { "clinic_1" },
                                                        doctorId = if (appRole == Role.DOCTOR) user.uid else null
                                                    )
                                                    ServiceLocator.sessionPreferences.saveSession(session)
                                                }
                                            }
                                        }
                                    },
                                    onAuthError = { err ->
                                        isGoogleSigningIn = false
                                        googleSignInError = err
                                    },
                                    scope = coroutineScope,
                                    onAuthCancelled = {
                                        isGoogleSigningIn = false
                                    }
                                )
                            },
                            enabled = !isGoogleSigningIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_sign_in_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isGoogleSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.continue_with_google_btn), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (googleSignInError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = googleSignInError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // OR Divider between Google Sign-In and Mobile Phone OTP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.or_divider),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        // Patient Phone OTP Form
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.name_label)) },
                            placeholder = { Text(stringResource(R.string.name_hint)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("patient_name_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text(stringResource(R.string.phone_label)) },
                            placeholder = { Text(stringResource(R.string.phone_hint)) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("patient_phone_input")
                        )

                        if (uiState.otpSent) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { otpCode = it },
                                label = { Text(stringResource(R.string.otp_label)) },
                                placeholder = { Text(stringResource(R.string.otp_hint)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                supportingText = { Text(uiState.otpMessage ?: "Enter 6-digit verification code") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("patient_otp_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { viewModel.verifyOtp(phoneNumber, otpCode, displayName) },
                                enabled = !uiState.isLoading && otpCode.length >= 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(stringResource(R.string.verify_btn), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))

                            FilledTonalButton(
                                onClick = { viewModel.requestOtp(phoneNumber) },
                                enabled = !uiState.isLoading && phoneNumber.length >= 10,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("request_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.send_otp_btn), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 4. SECONDARY SECTION: FOR CLINIC STAFF (No redundant Patient/Staff toggle)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(
                        width = if (showStaffSignInForm) 1.5.dp else 1.dp,
                        color = if (showStaffSignInForm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("clinic_onboarding_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.for_clinic_staff_header),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.staff_prompt_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    showStaffSignInForm = !showStaffSignInForm
                                    viewModel.clearError()
                                },
                                modifier = Modifier.testTag("staff_sign_in_cta_btn"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.staff_sign_in_btn),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showStaffSignInForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (showStaffSignInForm) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.username_label)) },
                                placeholder = { Text(stringResource(R.string.username_hint)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("staff_username_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(stringResource(R.string.password_label)) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("staff_password_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.staffLogin(username, password) },
                                enabled = !uiState.isLoading && username.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("staff_login_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(stringResource(R.string.login_btn), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Join Clinic via Staff Invite Code",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Doctors and Receptionists require a valid single-use clinic invitation code (e.g. DOC-7890 or REC-1234).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = staffInviteCode,
                                    onValueChange = {
                                        staffInviteCode = it.uppercase()
                                        staffInviteFeedback = null
                                    },
                                    label = { Text("Staff Invite Code") },
                                    placeholder = { Text("DOC-7890 / REC-1234") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("staff_invite_code_input")
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val actorId = username.ifBlank { "staff_candidate" }
                                        val res = StaffInvitationValidator.validateAndRedeemInvitation(
                                            userId = actorId,
                                            rawInviteCode = staffInviteCode
                                        )
                                        staffInviteIsError = !res.isSuccess
                                        staffInviteFeedback = res.message
                                        if (res.isSuccess) {
                                            when (res.assignedRole) {
                                                UserRole.DOCTOR -> viewModel.loginAsDemo(Role.DOCTOR)
                                                UserRole.RECEPTIONIST -> viewModel.loginAsDemo(Role.RECEPTIONIST)
                                                else -> {}
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("verify_staff_invite_btn")
                                ) {
                                    Text("Verify Invite", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            staffInviteFeedback?.let { msg ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (staffInviteIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("staff_invite_feedback")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showRoadmapModal = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_view_roadmap")
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guide", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = onNavigateToOnboarding,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(44.dp)
                                    .testTag("btn_start_onboarding"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Clinic & Staff Setup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
