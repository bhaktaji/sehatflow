package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.AppThemeMode
import com.example.core.payment.*
import com.example.core.update.AppUpdateManager
import com.example.core.update.UpdateUiState
import com.example.domain.model.DpdpPrivacyRegistry
import com.example.domain.model.PatientHealthProfile
import com.example.domain.model.PatientHealthProfileRegistry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SehatFlowSettingsDialog(
    currentServerUrl: String,
    isDemoMode: Boolean,
    currentThemeMode: AppThemeMode,
    isQueueSoundEnabled: Boolean,
    currentPatientId: String? = null,
    currentClinicId: String = "clinic_1",
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleQueueSound: (Boolean) -> Unit,
    onSaveServerConfig: (String, Boolean) -> Unit,
    onOpenHealthProfile: (() -> Unit)? = null,
    onOpenSubscriptionScreen: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }
    var selectedLanguage by remember { mutableStateOf("en") }
    var dpdpStatusMessage by remember { mutableStateOf<String?>(null) }
    val activePatientId = currentPatientId?.takeIf { it.isNotBlank() } ?: "pat_demo_patient"
    var consentRecord by remember(activePatientId) {
        mutableStateOf(DpdpPrivacyRegistry.getConsentRecord(activePatientId))
    }

    // Subscription state in Settings
    val allSubscriptions by SubscriptionPaymentEngine.subscriptionsFlow.collectAsStateWithLifecycle()
    val allTransactions by SubscriptionPaymentEngine.transactionsFlow.collectAsStateWithLifecycle()
    val currentSubscription = remember(currentClinicId, allSubscriptions) {
        SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId = currentClinicId)
    }
    val clinicTransactions = remember(currentClinicId, allTransactions) {
        SubscriptionPaymentEngine.getTransactionsForClinic(currentClinicId)
    }
    var selectedSettingsPlan by remember { mutableStateOf(SubscriptionPaymentEngine.AVAILABLE_PLANS.first()) }
    var settingsSubFeedback by remember { mutableStateOf<String?>(null) }
    var upiVpaInput by remember { mutableStateOf(SubscriptionPaymentEngine.upiConfig.upiVpa) }
    var payeeNameInput by remember { mutableStateOf(SubscriptionPaymentEngine.upiConfig.payeeName) }
    var upiNumberInput by remember { mutableStateOf(SubscriptionPaymentEngine.upiConfig.upiNumber) }
    var isEditingUpiConfig by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    // In-App Update state in Settings
    val updateUiState by AppUpdateManager.updateState.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. APPEARANCE (THEME SELECTOR - P0)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_appearance_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_appearance_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Choose how SehatFlow looks on your device. Changes apply immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val selected = currentThemeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectThemeMode(mode) },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                                AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                                AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_option_${mode.name}")
                                )
                            }
                        }
                    }
                }

                // 2. IN-APP SUBSCRIPTION MANAGEMENT SECTION
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_subscription_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardMembership,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Subscription & Billing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("settings_subscription_status_badge")
                            ) {
                                Text(
                                    text = currentSubscription.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Current Plan: ${currentSubscription.currentPlanName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("settings_current_plan_text")
                        )

                        val expiryDateStr = dateFormatter.format(
                            Date(
                                if (currentSubscription.status == SubscriptionLifecycleStatus.TRIAL)
                                    currentSubscription.trialEndsAtMillis
                                else currentSubscription.expiresAtMillis
                            )
                        )
                        Text(
                            text = "Renewal / Expiry Date: $expiryDateStr • Auto-Renew: ${if (currentSubscription.autoRenew) "On" else "Off"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("settings_subscription_expiry_text")
                        )

                        // Configurable Payee UPI ID
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Receiving UPI ID (Direct Payments)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = {
                                            if (isEditingUpiConfig) {
                                                SubscriptionPaymentEngine.updateUpiConfig(upiVpaInput, payeeNameInput, upiNumberInput)
                                                settingsSubFeedback = "Receiving UPI updated to $upiVpaInput"
                                            }
                                            isEditingUpiConfig = !isEditingUpiConfig
                                        }
                                    ) {
                                        Text(if (isEditingUpiConfig) "Save" else "Edit", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (isEditingUpiConfig) {
                                    OutlinedTextField(
                                        value = upiVpaInput,
                                        onValueChange = { upiVpaInput = it },
                                        label = { Text("UPI VPA") },
                                        placeholder = { Text("doctor@okaxis / clinic@okhdfcbank") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = payeeNameInput,
                                        onValueChange = { payeeNameInput = it },
                                        label = { Text("Payee Name") },
                                        placeholder = { Text("Clinic / Doctor Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = upiNumberInput,
                                        onValueChange = { upiNumberInput = it },
                                        label = { Text("UPI Phone Number") },
                                        placeholder = { Text("9876543210 (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "${SubscriptionPaymentEngine.upiConfig.upiVpa} (${SubscriptionPaymentEngine.upiConfig.payeeName}" +
                                            if (SubscriptionPaymentEngine.upiConfig.upiNumber.isNotBlank()) " • ${SubscriptionPaymentEngine.upiConfig.upiNumber})" else ")",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubscriptionPaymentEngine.AVAILABLE_PLANS.forEach { plan ->
                                val selected = selectedSettingsPlan.planId == plan.planId
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedSettingsPlan = plan },
                                    label = {
                                        Text(
                                            text = "₹${plan.priceInr}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_plan_chip_${plan.planId}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val order = SubscriptionPaymentEngine.createPaymentOrder(
                                        clinicId = currentClinicId,
                                        adminUid = activePatientId,
                                        planId = selectedSettingsPlan.planId,
                                        autoRenew = true
                                    )
                                    val payId = "pay_rzp_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                                    val sig = SubscriptionPaymentEngine.computeOrderPaymentSignature(order.orderId, payId)
                                    val verified = SubscriptionPaymentEngine.verifyPaymentAndActivate(
                                        orderId = order.orderId,
                                        gatewayPaymentId = payId,
                                        gatewaySignature = sig,
                                        paymentMetadata = SafePaymentMetadata(
                                            method = PaymentGatewayMethod.UPI,
                                            maskedInstrument = "clinic@okhdfcbank"
                                        )
                                    )
                                    if (verified is PaymentVerificationOutcome.Activated) {
                                        settingsSubFeedback = "Activated ${verified.subscription.currentPlanName} (Ref: ${verified.transaction.transactionId})"
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_purchase_plan_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Upgrade (${selectedSettingsPlan.billingCycleLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (currentSubscription.status == SubscriptionLifecycleStatus.ACTIVE) {
                                OutlinedButton(
                                    onClick = {
                                        val cancelled = SubscriptionPaymentEngine.cancelSubscription(
                                            clinicId = currentClinicId,
                                            reason = "Cancelled from Settings"
                                        )
                                        settingsSubFeedback = "Auto-renewal cancelled. Active until ${dateFormatter.format(Date(cancelled.expiresAtMillis))}."
                                    },
                                    modifier = Modifier.testTag("settings_cancel_subscription_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (clinicTransactions.isNotEmpty()) {
                            val latestTxn = clinicTransactions.first()
                            Text(
                                text = "Latest Payment: ₹${latestTxn.amountInr} • ${latestTxn.status.name} (${latestTxn.transactionId})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("settings_latest_payment_text")
                            )
                        }

                        if (onOpenSubscriptionScreen != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenSubscriptionScreen()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_full_subscription_screen_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open Full Subscription & Invoice Portal", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        settingsSubFeedback?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("settings_subscription_feedback_text")
                            )
                        }
                    }
                }

                // 3. IN-APP UPDATES SECTION (Settings -> Check for Updates)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_check_updates_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Installed: Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    AppUpdateManager.checkForUpdates()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_check_for_updates"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check for Updates")
                        }

                        when (val state = updateUiState) {
                            is UpdateUiState.Idle -> Unit
                            is UpdateUiState.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "Checking official SehatFlow HTTPS release manifest...",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            is UpdateUiState.UpToDate -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_status_up_to_date_card")
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            is UpdateUiState.UpdateAvailable -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (state.isMandatory) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_available_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (state.isMandatory) {
                                                "MANDATORY UPDATE: SehatFlow v${state.manifest.latestVersionName} (Build ${state.manifest.latestVersionCode})"
                                            } else {
                                                "Update Available: SehatFlow v${state.manifest.latestVersionName} (Build ${state.manifest.latestVersionCode})"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        state.manifest.releaseNotes.forEach { note ->
                                            Text(
                                                text = "• $note",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    AppUpdateManager.downloadAndVerifyUpdate(
                                                        context = context,
                                                        manifest = state.manifest
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("btn_update_now")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Update Now")
                                        }
                                    }
                                }
                            }
                            is UpdateUiState.Downloading -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.testTag("update_downloading_box")
                                ) {
                                    Text(
                                        text = "Downloading v${state.manifest.latestVersionName}... ${state.progress.progressPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LinearProgressIndicator(
                                        progress = { state.progress.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            is UpdateUiState.ReadyToInstall -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_ready_to_install_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "SHA-256 & Package Signature Verified (v${state.manifest.latestVersionName})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Checksum: ${state.computedSha256.take(16)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Button(
                                            onClick = {
                                                runCatching {
                                                    val installIntent = AppUpdateManager.createInstallApkIntent(
                                                        context = context,
                                                        verifiedApkFile = state.verifiedApkFile
                                                    )
                                                    context.startActivity(installIntent)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("btn_install_verified_update")
                                        ) {
                                            Text("Install Verified Update")
                                        }
                                    }
                                }
                            }
                            is UpdateUiState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_error_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = state.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (state.retryManifest != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        AppUpdateManager.downloadAndVerifyUpdate(
                                                            context = context,
                                                            manifest = state.retryManifest
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("btn_retry_update")
                                            ) {
                                                Text("Retry Download")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. QUEUE NOTIFICATIONS & ALERTS (P1)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Queue Turn Sound & Vibration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Vibrate and chime when your token is called or next in line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isQueueSoundEnabled,
                            onCheckedChange = onToggleQueueSound,
                            modifier = Modifier.testTag("settings_queue_sound_toggle")
                        )
                    }
                }

                // 5. OPTIONAL HEALTH PROFILE & DPDP PRIVACY CONTROLS (P1)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_privacy_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Health Profile & Privacy Rights (DPDP)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (onOpenHealthProfile != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenHealthProfile()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_health_profile_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Manage Optional Health Profile")
                            }
                        }

                        Text(
                            text = "Privacy Notice Language (Eighth Schedule):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("en" to "English", "hi" to "हिन्दी", "bn" to "বাংলা").forEach { (code, label) ->
                                FilterChip(
                                    selected = selectedLanguage == code,
                                    onClick = { selectedLanguage = code },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("privacy_lang_$code")
                                )
                            }
                        }
                        Text(
                            text = DpdpPrivacyRegistry.SUPPORTED_NOTICE_LANGUAGES[selectedLanguage]
                                ?: DpdpPrivacyRegistry.SUPPORTED_NOTICE_LANGUAGES.getValue("en"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val res = DpdpPrivacyRegistry.withdrawOptionalHealthProfileConsent(activePatientId)
                                    consentRecord = DpdpPrivacyRegistry.getConsentRecord(activePatientId)
                                    dpdpStatusMessage = res.resolutionSummary
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("withdraw_health_consent_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Withdraw Health Consent", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    val res = DpdpPrivacyRegistry.submitDataErasureOrAccountDeletionRequest(
                                        userId = activePatientId,
                                        hasRetainedClinicalConsultations = true
                                    )
                                    consentRecord = DpdpPrivacyRegistry.getConsentRecord(activePatientId)
                                    dpdpStatusMessage = res.resolutionSummary
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_data_erasure_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Request Erasure", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Text(
                            text = "Notice Version: ${consentRecord.noticeVersion} • Optional Health Consent: ${if (consentRecord.optionalHealthProfileConsentGranted) "Granted" else "Not Granted / Withdrawn"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        dpdpStatusMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // 6. SERVER CONFIGURATION
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.server_settings_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text(stringResource(R.string.server_url_label)) },
                            placeholder = { Text(stringResource(R.string.server_url_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_url_input")
                        )
                    }
                }

                // 7. ABOUT, HELP & LEGAL SUMMARY (P2)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "About SehatFlow",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE}) • Real-time Clinic Token & Queue Platform",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Support & Grievance Officer: privacy-grievance@sehatflow.app • Helpline: 1800-120-SEHAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveServerConfig(urlInput, false) },
                modifier = Modifier.testTag("save_server_settings_btn")
            ) {
                Text(stringResource(R.string.save_settings_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_btn))
            }
        }
    )
}

/**
 * Optional Patient Health Profile Dialog (Specification Section 7).
 * Allows patients to provide optional self-reported health details with explicit DPDP consent,
 * or click "Skip for now" without fabricating defaults or blocking queue access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHealthProfileDialog(
    patientId: String,
    onCompletedOrSkipped: (PatientHealthProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val existing = remember(patientId) {
        PatientHealthProfileRegistry.getAuthorizedHealthProfile(
            patientId = patientId,
            session = com.example.domain.model.UserSession(
                token = "self",
                userId = patientId,
                displayName = "Patient",
                role = com.example.domain.model.Role.PATIENT,
                clinicId = null,
                doctorId = null
            )
        )
    }

    var dobOrAge by remember(existing) { mutableStateOf(existing?.dateOfBirthOrAge ?: "") }
    var selectedBloodGroup by remember(existing) { mutableStateOf(existing?.bloodGroup ?: "") }
    var heightCm by remember(existing) { mutableStateOf(existing?.heightCm?.toString() ?: "") }
    var weightKg by remember(existing) { mutableStateOf(existing?.weightKg?.toString() ?: "") }
    var allergies by remember(existing) { mutableStateOf(existing?.knownAllergies ?: "") }
    var conditions by remember(existing) { mutableStateOf(existing?.existingConditions ?: "") }
    var medications by remember(existing) { mutableStateOf(existing?.currentMedications ?: "") }
    var emergencyContact by remember(existing) { mutableStateOf(existing?.emergencyContact ?: "") }
    var consentChecked by remember(existing) { mutableStateOf(existing?.consentGranted ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val bloodGroupOptions = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.health_profile_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.health_profile_unverified_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.health_profile_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                validationError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = dobOrAge,
                    onValueChange = { dobOrAge = it; validationError = null },
                    label = { Text("Age or Date of Birth (Optional)") },
                    placeholder = { Text("e.g. 34 yrs or 1992-05-14") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_dob_input")
                )

                Text(
                    text = "Blood Group (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bloodGroupOptions.take(4).forEach { bg ->
                        FilterChip(
                            selected = selectedBloodGroup == bg,
                            onClick = { selectedBloodGroup = if (selectedBloodGroup == bg) "" else bg },
                            label = { Text(bg, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bloodGroupOptions.drop(4).forEach { bg ->
                        FilterChip(
                            selected = selectedBloodGroup == bg,
                            onClick = { selectedBloodGroup = if (selectedBloodGroup == bg) "" else bg },
                            label = { Text(bg, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = heightCm,
                        onValueChange = { heightCm = it; validationError = null },
                        label = { Text("Height (cm)") },
                        placeholder = { Text("170") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("health_height_input")
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it; validationError = null },
                        label = { Text("Weight (kg)") },
                        placeholder = { Text("68") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("health_weight_input")
                    )
                }

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it; validationError = null },
                    label = { Text("Known Allergies (Optional)") },
                    placeholder = { Text("e.g. Penicillin, Peanuts") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_allergies_input")
                )

                OutlinedTextField(
                    value = conditions,
                    onValueChange = { conditions = it; validationError = null },
                    label = { Text("Existing Medical Conditions (Optional)") },
                    placeholder = { Text("e.g. Hypertension, Type 2 Diabetes") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_conditions_input")
                )

                OutlinedTextField(
                    value = medications,
                    onValueChange = { medications = it; validationError = null },
                    label = { Text("Current Medications (Optional)") },
                    placeholder = { Text("e.g. Metformin 500mg") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_medications_input")
                )

                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it; validationError = null },
                    label = { Text("Emergency Contact (Optional)") },
                    placeholder = { Text("e.g. +91-9830011223 (Spouse)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_emergency_input")
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it; validationError = null },
                            modifier = Modifier.testTag("health_profile_consent_checkbox")
                        )
                        Text(
                            text = "I consent to share these optional self-reported health details with my treating doctor for consultation preparation. I can withdraw consent anytime in Settings.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = PatientHealthProfileRegistry.saveOrUpdateHealthProfile(
                        patientId = patientId,
                        dateOfBirthOrAge = dobOrAge,
                        bloodGroup = selectedBloodGroup,
                        heightCmInput = heightCm,
                        weightKgInput = weightKg,
                        knownAllergies = allergies,
                        existingConditions = conditions,
                        currentMedications = medications,
                        emergencyContact = emergencyContact,
                        consentGranted = consentChecked
                    )
                    result.onSuccess { saved ->
                        onCompletedOrSkipped(saved)
                    }.onFailure { err ->
                        validationError = err.message ?: "Please check your inputs."
                    }
                },
                modifier = Modifier.testTag("health_profile_save_btn")
            ) {
                Text(stringResource(R.string.health_profile_save_btn))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val skipped = PatientHealthProfileRegistry.skipHealthProfileOnboarding(patientId)
                    onCompletedOrSkipped(skipped)
                },
                modifier = Modifier.testTag("health_profile_skip_btn")
            ) {
                Text(stringResource(R.string.health_profile_skip_btn), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
