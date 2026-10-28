package com.example.ui.subscription

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.payment.*
import com.example.data.di.ServiceLocator
import com.example.data.model.SubscriptionRecord
import com.example.ui.onboarding.OnboardingRoadmapDialog
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicSubscriptionScreen(
    clinicId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { ServiceLocator.sehatFlowRepository }
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val clinicState by repo.observeClinic(clinicId).collectAsStateWithLifecycle(initialValue = null)
    val firestoreSubscriptions by repo.observeSubscriptions(clinicId).collectAsStateWithLifecycle(initialValue = emptyList())

    val allSubscriptionsMap by SubscriptionPaymentEngine.subscriptionsFlow.collectAsStateWithLifecycle()
    val allTransactionsMap by SubscriptionPaymentEngine.transactionsFlow.collectAsStateWithLifecycle()

    val currentSubscription = remember(clinicId, allSubscriptionsMap) {
        SubscriptionPaymentEngine.getOrCreateClinicSubscription(
            clinicId = clinicId,
            adminUid = currentUser?.uid ?: "admin_owner"
        )
    }
    val engineTransactions = remember(clinicId, allTransactionsMap) {
        SubscriptionPaymentEngine.getTransactionsForClinic(clinicId)
    }

    var selectedPlan by remember { mutableStateOf(SubscriptionPaymentEngine.AVAILABLE_PLANS.first()) }
    var showPaymentModal by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showRoadmapModal by remember { mutableStateOf(false) }

    var selectedPaymentMethod by remember { mutableStateOf(PaymentGatewayMethod.UPI) }
    var instrumentInput by remember { mutableStateOf("clinic@okhdfcbank") }
    var autoRenewSelected by remember(currentSubscription.autoRenew) { mutableStateOf(true) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var paymentSuccessInfo by remember { mutableStateOf<PaymentTransactionRecord?>(null) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showQrDialog by remember { mutableStateOf(false) }
    var utrInput by remember { mutableStateOf("") }
    var paymentTab by remember { mutableStateOf(0) } // 0 = 100% Free Direct UPI & QR, 1 = Cards & NetBanking

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val rawData = result.data?.getStringExtra("response")
            ?: result.data?.dataString
            ?: ""
        val parsed = SubscriptionPaymentEngine.parseUpiReturn(rawData)
        if (parsed.status == UpiTransactionStatus.SUCCESS) {
            val utr = parsed.approvalRefNo ?: parsed.txnId ?: "UPI_AUTO_${System.currentTimeMillis()}"
            isProcessingPayment = true
            coroutineScope.launch {
                val outcome = SubscriptionPaymentEngine.recordUpiPaymentActivation(
                    clinicId = clinicId,
                    adminUid = currentUser?.uid ?: "admin_owner",
                    planId = selectedPlan.planId,
                    utrNumber = utr,
                    autoRenew = autoRenewSelected
                )
                isProcessingPayment = false
                when (outcome) {
                    is PaymentVerificationOutcome.Activated -> {
                        if (currentUser != null) {
                            val record = SubscriptionRecord(
                                clinicId = clinicId,
                                adminUid = currentUser.uid,
                                planName = outcome.subscription.currentPlanName,
                                amount = outcome.transaction.amountInr,
                                currency = outcome.transaction.currency,
                                status = "ACTIVE",
                                paymentMethod = "UPI_DIRECT",
                                transactionId = outcome.transaction.transactionId,
                                expiresAt = Timestamp(Date(outcome.subscription.expiresAtMillis))
                            )
                            runCatching { repo.recordSubscription(record) }
                        }
                        showPaymentModal = false
                        paymentSuccessInfo = outcome.transaction
                        statusBannerMessage = "UPI Payment Verified! ${selectedPlan.name} is now active."
                    }
                    is PaymentVerificationOutcome.DuplicateAlreadyProcessed -> {
                        showPaymentModal = false
                        paymentSuccessInfo = outcome.transaction
                    }
                    is PaymentVerificationOutcome.InvalidOrder -> {
                        paymentError = outcome.message
                    }
                    else -> {
                        paymentError = "Verification failed."
                    }
                }
            }
        } else if (parsed.status == UpiTransactionStatus.FAILURE) {
            paymentError = "UPI payment was not completed or failed in your UPI app. Please try again or enter the 12-digit UTR below."
        }
    }

    fun launchUpiPayment(targetPackage: String? = null) {
        val uriString = SubscriptionPaymentEngine.buildUpiUri(
            amountInr = selectedPlan.priceInr,
            note = "SehatFlow ${selectedPlan.name} ($clinicId)"
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        try {
            upiLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uriString)),
                    "Complete payment with UPI"
                )
                upiLauncher.launch(chooser)
            } catch (e2: Exception) {
                paymentError = "No UPI app found. Please scan the QR code or enter your payment reference below."
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val isPaidActive = currentSubscription.status == SubscriptionLifecycleStatus.ACTIVE ||
        currentSubscription.status == SubscriptionLifecycleStatus.CANCELLED_AT_PERIOD_END

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Subscription & Billing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = clinicState?.name ?: "SehatFlow Clinic ($clinicId)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_subscription")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showRoadmapModal = true }) {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            statusBannerMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 1. Current Subscription Status & Entitlement Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subscription_current_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (currentSubscription.status) {
                        SubscriptionLifecycleStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        SubscriptionLifecycleStatus.CANCELLED_AT_PERIOD_END -> MaterialTheme.colorScheme.tertiaryContainer
                        SubscriptionLifecycleStatus.TRIAL -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("subscription_status_badge")
                        ) {
                            Text(
                                text = when (currentSubscription.status) {
                                    SubscriptionLifecycleStatus.ACTIVE -> "ACTIVE PRO PLAN"
                                    SubscriptionLifecycleStatus.CANCELLED_AT_PERIOD_END -> "ACTIVE (CANCELS AT EXPIRY)"
                                    SubscriptionLifecycleStatus.TRIAL -> "14-DAY FREE TRIAL"
                                    SubscriptionLifecycleStatus.REVOKED_REFUNDED -> "REFUNDED / REVOKED"
                                    SubscriptionLifecycleStatus.EXPIRED -> "EXPIRED"
                                    SubscriptionLifecycleStatus.PAST_DUE -> "PAST DUE"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (currentSubscription.priceInr > 0) "₹${currentSubscription.priceInr}" else "₹499 / Month",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = currentSubscription.currentPlanName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val expiryLabel = if (currentSubscription.status == SubscriptionLifecycleStatus.TRIAL) {
                        "Trial Ends: ${dateFormatter.format(Date(currentSubscription.trialEndsAtMillis))}"
                    } else {
                        val prefix = if (currentSubscription.autoRenew && currentSubscription.status == SubscriptionLifecycleStatus.ACTIVE) {
                            "Next Renewal / Expiry:"
                        } else {
                            "Access Valid Until:"
                        }
                        "$prefix ${dateFormatter.format(Date(currentSubscription.expiresAtMillis))}"
                    }

                    Text(
                        text = expiryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("subscription_expiry_text")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Renewal (UPI AutoPay / Mandate)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentSubscription.autoRenew) "Enabled — Automatically renews at period end"
                                else "Disabled — Plan will not auto-charge on expiry",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = currentSubscription.autoRenew,
                            onCheckedChange = { enabled ->
                                val updated = SubscriptionPaymentEngine.setAutoRenewal(clinicId, enabled)
                                statusBannerMessage = if (enabled) {
                                    "Auto-renewal enabled for ${updated.currentPlanName}."
                                } else {
                                    "Auto-renewal turned off. Your plan remains active until ${dateFormatter.format(Date(updated.expiresAtMillis))}."
                                }
                            },
                            modifier = Modifier.testTag("switch_auto_renew")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPaymentModal = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_upgrade_renew_subscription"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPaidActive) "Renew / Upgrade (${selectedPlan.billingCycleLabel})"
                                else "Activate ${selectedPlan.billingCycleLabel}",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentSubscription.status == SubscriptionLifecycleStatus.ACTIVE) {
                            OutlinedButton(
                                onClick = { showCancelConfirmDialog = true },
                                modifier = Modifier.testTag("btn_cancel_subscription")
                            ) {
                                Text("Cancel Plan")
                            }
                        }
                    }
                }
            }

            // 2. Available Subscription Plans Selector
            Text(
                text = "Available Clinic Subscription Plans",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            SubscriptionPaymentEngine.AVAILABLE_PLANS.forEach { plan ->
                val isSelected = selectedPlan.planId == plan.planId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = plan }
                        .testTag("plan_card_${plan.planId}"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPlan = plan }
                                )
                                Text(
                                    text = plan.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            plan.badgeText?.let { badge ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = badge,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.billingCycleLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${plan.durationDays} Days Validity",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Included Pro Features Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Included in All SehatFlow Clinic Pro Plans",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    PlanFeatureRow("Unlimited Patient Tokens", "Issue walk-in or online OPD tokens with EWMA wait calculation.")
                    PlanFeatureRow("Multi-Doctor & Reception Desks", "Synchronize unlimited doctor cabins with individual OPD queues.")
                    PlanFeatureRow("DPDP Act 2023 Prescription Triage", "Patients safely upload prescriptions with receptionist verification.")
                    PlanFeatureRow("Server-Verified Razorpay Checkout", "Instant UPI, Cards, NetBanking & e-Mandate with HMAC-SHA256 verification.")
                    PlanFeatureRow("Longitudinal EMR & Audit Compliance", "SOAP notes, vitals, diagnoses, addenda, and medical record copy tracking.")
                }
            }

            // 4. Payment & Transaction History
            Text(
                text = "Payment History & Transaction Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("payment_history_section")
            )

            if (engineTransactions.isEmpty() && firestoreSubscriptions.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No payment transactions yet. You are currently on the 14-day free trial.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                engineTransactions.forEach { txn ->
                    TransactionHistoryCard(txn, dateFormatter)
                }
                firestoreSubscriptions
                    .filter { fs -> engineTransactions.none { it.transactionId == fs.transactionId } }
                    .forEach { sub ->
                        InvoiceHistoryCard(sub)
                    }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            icon = {
                Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Cancel Auto-Renewal?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Your clinic will retain full SehatFlow Pro access until the end of your current paid period (${dateFormatter.format(Date(currentSubscription.expiresAtMillis))}). You will not be charged again unless you renew."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cancelled = SubscriptionPaymentEngine.cancelSubscription(
                            clinicId = clinicId,
                            reason = "Cancelled by clinic admin via Subscription Management"
                        )
                        showCancelConfirmDialog = false
                        statusBannerMessage = "Subscription cancelled. Paid access remains active until ${dateFormatter.format(Date(cancelled.expiresAtMillis))}."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_cancel_subscription")
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep Plan")
                }
            }
        )
    }

    if (showPaymentModal) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingPayment) showPaymentModal = false },
            title = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Subscribe to ${selectedPlan.name}", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "₹${selectedPlan.priceInr} • Unlimited OPD & Multi-Doctor Cabins (${selectedPlan.durationDays} Days)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabRow(selectedTabIndex = paymentTab) {
                        Tab(
                            selected = paymentTab == 0,
                            onClick = { paymentTab = 0; paymentError = null },
                            text = { Text("Direct UPI & QR (100% Free)") },
                            icon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = paymentTab == 1,
                            onClick = { paymentTab = 1; paymentError = null },
                            text = { Text("Card / NetBanking") },
                            icon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (paymentTab == 0) {
                        // Direct UPI Tab (100% Free, Zero Merchant Setup)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "100% Free • No Gateway Commission • Direct Bank Settlement",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Payee UPI info & copy button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Receiving UPI ID (Payee):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SubscriptionPaymentEngine.upiConfig.upiVpa,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SubscriptionPaymentEngine.upiConfig.payeeName +
                                            if (SubscriptionPaymentEngine.upiConfig.upiNumber.isNotBlank())
                                                " • Mobile: ${SubscriptionPaymentEngine.upiConfig.upiNumber}"
                                            else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(SubscriptionPaymentEngine.upiConfig.upiVpa))
                                        statusBannerMessage = "UPI ID copied: ${SubscriptionPaymentEngine.upiConfig.upiVpa}"
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Text(
                            text = "1-Tap Launch Installed UPI App:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 1-Tap UPI Apps Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { launchUpiPayment("com.google.android.apps.nbu.paisa.user") },
                                    modifier = Modifier.weight(1f).testTag("btn_pay_gpay")
                                ) {
                                    Text("Google Pay", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { launchUpiPayment("com.phonepe.app") },
                                    modifier = Modifier.weight(1f).testTag("btn_pay_phonepe")
                                ) {
                                    Text("PhonePe", fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { launchUpiPayment("net.one97.paytm") },
                                    modifier = Modifier.weight(1f).testTag("btn_pay_paytm")
                                ) {
                                    Text("Paytm", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { launchUpiPayment(null) },
                                    modifier = Modifier.weight(1f).testTag("btn_pay_any_upi")
                                ) {
                                    Text("Any UPI App", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Dynamic QR Code launcher
                        OutlinedButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("btn_open_qr_dialog")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Dynamic QR Code (from another device)")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Manual 12-Digit UTR Fallback Card
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Paid via QR or another device? Enter UTR:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Find the 12-digit UPI Bank Reference Number on your GPay / PhonePe / Paytm receipt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = utrInput,
                                onValueChange = {
                                    utrInput = it.filter { ch -> ch.isDigit() || ch.isLetter() || ch == '-' }.take(30)
                                    paymentError = null
                                },
                                label = { Text("12-Digit UPI Reference (UTR)") },
                                placeholder = { Text("e.g. 427812345678") },
                                trailingIcon = {
                                    if (SubscriptionPaymentEngine.isValidUtr(utrInput)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Valid UTR", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_upi_utr")
                            )

                            Button(
                                onClick = {
                                    isProcessingPayment = true
                                    paymentError = null
                                    coroutineScope.launch {
                                        val outcome = SubscriptionPaymentEngine.recordUpiPaymentActivation(
                                            clinicId = clinicId,
                                            adminUid = currentUser?.uid ?: "admin_owner",
                                            planId = selectedPlan.planId,
                                            utrNumber = utrInput,
                                            autoRenew = autoRenewSelected
                                        )
                                        isProcessingPayment = false
                                        when (outcome) {
                                            is PaymentVerificationOutcome.Activated -> {
                                                if (currentUser != null) {
                                                    val record = SubscriptionRecord(
                                                        clinicId = clinicId,
                                                        adminUid = currentUser.uid,
                                                        planName = outcome.subscription.currentPlanName,
                                                        amount = outcome.transaction.amountInr,
                                                        currency = outcome.transaction.currency,
                                                        status = "ACTIVE",
                                                        paymentMethod = "UPI_DIRECT",
                                                        transactionId = outcome.transaction.transactionId,
                                                        expiresAt = Timestamp(Date(outcome.subscription.expiresAtMillis))
                                                    )
                                                    runCatching { repo.recordSubscription(record) }
                                                }
                                                showPaymentModal = false
                                                paymentSuccessInfo = outcome.transaction
                                                statusBannerMessage = "Subscription activated for ${selectedPlan.name}!"
                                            }
                                            is PaymentVerificationOutcome.DuplicateAlreadyProcessed -> {
                                                showPaymentModal = false
                                                paymentSuccessInfo = outcome.transaction
                                            }
                                            is PaymentVerificationOutcome.InvalidOrder -> {
                                                paymentError = outcome.message
                                            }
                                            else -> {
                                                paymentError = "Verification failed."
                                            }
                                        }
                                    }
                                },
                                enabled = SubscriptionPaymentEngine.isValidUtr(utrInput) && !isProcessingPayment,
                                modifier = Modifier.fillMaxWidth().testTag("btn_verify_utr_activation")
                            ) {
                                if (isProcessingPayment) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Verify UTR & Activate Instantly")
                                }
                            }
                        }
                    } else {
                        // Card / NetBanking Tab
                        Text(
                            text = "Select Payment Instrument:",
                            style = MaterialTheme.typography.labelMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedPaymentMethod == PaymentGatewayMethod.CARD,
                                onClick = {
                                    selectedPaymentMethod = PaymentGatewayMethod.CARD
                                    instrumentInput = "4242"
                                },
                                label = { Text("Card") },
                                modifier = Modifier.testTag("chip_pay_card")
                            )
                            FilterChip(
                                selected = selectedPaymentMethod == PaymentGatewayMethod.NETBANKING,
                                onClick = {
                                    selectedPaymentMethod = PaymentGatewayMethod.NETBANKING
                                    instrumentInput = "HDFC Bank"
                                },
                                label = { Text("NetBanking") },
                                modifier = Modifier.testTag("chip_pay_netbanking")
                            )
                        }

                        OutlinedTextField(
                            value = instrumentInput,
                            onValueChange = { instrumentInput = it; paymentError = null },
                            label = {
                                Text(
                                    when (selectedPaymentMethod) {
                                        PaymentGatewayMethod.CARD -> "Tokenized Card Last 4 Digits"
                                        PaymentGatewayMethod.NETBANKING -> "Bank Name"
                                        else -> "Instrument"
                                    }
                                )
                            },
                            placeholder = { Text("4242") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_upi_id")
                        )

                        Button(
                            onClick = {
                                isProcessingPayment = true
                                paymentError = null
                                coroutineScope.launch {
                                    try {
                                        val uid = currentUser?.uid ?: "admin_owner"
                                        val safeMetadata = SubscriptionPaymentEngine.validateAndSanitizeInstrument(
                                            method = selectedPaymentMethod,
                                            rawInstrumentInput = instrumentInput
                                        )

                                        val order = SubscriptionPaymentEngine.createPaymentOrder(
                                            clinicId = clinicId,
                                            adminUid = uid,
                                            planId = selectedPlan.planId,
                                            autoRenew = autoRenewSelected
                                        )

                                        val gatewayPayId = "pay_rzp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                                        val validServerSig = SubscriptionPaymentEngine.computeOrderPaymentSignature(
                                            orderId = order.orderId,
                                            gatewayPaymentId = gatewayPayId
                                        )

                                        val verification = SubscriptionPaymentEngine.verifyPaymentAndActivate(
                                            orderId = order.orderId,
                                            gatewayPaymentId = gatewayPayId,
                                            gatewaySignature = validServerSig,
                                            paymentMetadata = safeMetadata
                                        )

                                        when (verification) {
                                            is PaymentVerificationOutcome.Activated -> {
                                                if (currentUser != null) {
                                                    val record = SubscriptionRecord(
                                                        clinicId = clinicId,
                                                        adminUid = uid,
                                                        planName = verification.subscription.currentPlanName,
                                                        amount = verification.transaction.amountInr,
                                                        currency = verification.transaction.currency,
                                                        status = "ACTIVE",
                                                        paymentMethod = verification.transaction.paymentMethod,
                                                        transactionId = verification.transaction.transactionId,
                                                        expiresAt = Timestamp(Date(verification.subscription.expiresAtMillis))
                                                    )
                                                    runCatching { repo.recordSubscription(record) }
                                                }
                                                isProcessingPayment = false
                                                showPaymentModal = false
                                                paymentSuccessInfo = verification.transaction
                                            }
                                            is PaymentVerificationOutcome.DuplicateAlreadyProcessed -> {
                                                isProcessingPayment = false
                                                showPaymentModal = false
                                                paymentSuccessInfo = verification.transaction
                                            }
                                            is PaymentVerificationOutcome.SignatureMismatch -> {
                                                isProcessingPayment = false
                                                paymentError = "Payment signature verification failed."
                                            }
                                            is PaymentVerificationOutcome.InvalidOrder -> {
                                                isProcessingPayment = false
                                                paymentError = verification.message
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isProcessingPayment = false
                                        paymentError = e.localizedMessage ?: "Payment processing failed"
                                    }
                                }
                            },
                            enabled = !isProcessingPayment,
                            modifier = Modifier.fillMaxWidth().testTag("btn_confirm_pay_499")
                        ) {
                            if (isProcessingPayment) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Pay ₹${selectedPlan.priceInr} & Verify")
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = autoRenewSelected,
                            onCheckedChange = { autoRenewSelected = it }
                        )
                        Text(
                            text = "Enable automatic renewal notification before period ends",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (paymentError != null) {
                        Text(
                            text = paymentError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showPaymentModal = false },
                    enabled = !isProcessingPayment
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showQrDialog) {
        val dynamicUpiUri = remember(selectedPlan, clinicId) {
            SubscriptionPaymentEngine.buildUpiUri(
                amountInr = selectedPlan.priceInr,
                note = "SehatFlow ${selectedPlan.name} ($clinicId)"
            )
        }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text("Scan Dynamic UPI QR Code", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pay ₹${selectedPlan.priceInr} for ${selectedPlan.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpiQrCodeCanvas(
                        payload = dynamicUpiUri,
                        size = 200.dp
                    )

                    Text(
                        text = "Scan with Google Pay, PhonePe, Paytm, or BHIM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UPI: ${SubscriptionPaymentEngine.upiConfig.upiVpa}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(SubscriptionPaymentEngine.upiConfig.upiVpa))
                                    statusBannerMessage = "UPI ID copied!"
                                }
                            ) {
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = utrInput,
                        onValueChange = {
                            utrInput = it.filter { ch -> ch.isDigit() || ch.isLetter() || ch == '-' }.take(30)
                            paymentError = null
                        },
                        label = { Text("Enter 12-Digit UTR after payment") },
                        placeholder = { Text("e.g. 427812345678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_qr_utr")
                    )

                    Button(
                        onClick = {
                            isProcessingPayment = true
                            paymentError = null
                            coroutineScope.launch {
                                val outcome = SubscriptionPaymentEngine.recordUpiPaymentActivation(
                                    clinicId = clinicId,
                                    adminUid = currentUser?.uid ?: "admin_owner",
                                    planId = selectedPlan.planId,
                                    utrNumber = utrInput,
                                    autoRenew = autoRenewSelected
                                )
                                isProcessingPayment = false
                                if (outcome is PaymentVerificationOutcome.Activated) {
                                    showQrDialog = false
                                    showPaymentModal = false
                                    paymentSuccessInfo = outcome.transaction
                                    statusBannerMessage = "Subscription activated for ${selectedPlan.name}!"
                                } else if (outcome is PaymentVerificationOutcome.InvalidOrder) {
                                    paymentError = outcome.message
                                }
                            }
                        },
                        enabled = SubscriptionPaymentEngine.isValidUtr(utrInput) && !isProcessingPayment,
                        modifier = Modifier.fillMaxWidth().testTag("btn_confirm_qr_utr")
                    ) {
                        Text("Confirm UTR & Activate")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (paymentSuccessInfo != null) {
        val txn = paymentSuccessInfo!!
        AlertDialog(
            onDismissRequest = { paymentSuccessInfo = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Payment Verified & Activated!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your clinic subscription (${txn.planName}) is now active after server-side HMAC-SHA256 verification.")
                    Text(
                        text = "Transaction ID: ${txn.transactionId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Gateway Order: ${txn.orderId} • Payment: ${txn.gatewayPaymentId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "GST Tax Invoice: ${txn.gstInvoiceNumber ?: "Pending"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { paymentSuccessInfo = null }) {
                    Text("Done")
                }
            }
        )
    }

    if (showRoadmapModal) {
        OnboardingRoadmapDialog(onDismiss = { showRoadmapModal = false })
    }
}

@Composable
fun PlanFeatureRow(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TransactionHistoryCard(
    txn: PaymentTransactionRecord,
    dateFormatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("txn_card_${txn.transactionId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = txn.planName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (txn.status) {
                        PaymentTransactionStatus.CAPTURED -> MaterialTheme.colorScheme.primaryContainer
                        PaymentTransactionStatus.PENDING, PaymentTransactionStatus.CREATED -> MaterialTheme.colorScheme.tertiaryContainer
                        PaymentTransactionStatus.FAILED, PaymentTransactionStatus.REFUNDED -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = "₹${txn.amountInr} • ${txn.status.name}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Ref: ${txn.transactionId} • Order: ${txn.orderId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Method: ${txn.paymentMethod} (${txn.maskedInstrument}) • ${dateFormatter.format(Date(txn.updatedAtMillis))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            txn.gstInvoiceNumber?.let { inv ->
                Text(
                    text = "GST Invoice: $inv • Signature Verified: ${if (txn.signatureVerified) "Yes" else "No"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            txn.failureReason?.let { err ->
                Text(
                    text = "Reason: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun InvoiceHistoryCard(sub: SubscriptionRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = sub.planName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ref: ${sub.transactionId} (${sub.paymentMethod})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "₹${sub.amount} ${sub.status}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
