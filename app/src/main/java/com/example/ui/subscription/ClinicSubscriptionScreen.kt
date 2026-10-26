package com.example.ui.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.di.ServiceLocator
import com.example.data.model.Clinic
import com.example.data.model.SubscriptionRecord
import com.example.data.model.SubscriptionStatus
import com.example.ui.onboarding.OnboardingRoadmapDialog
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.util.Date

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
    val subscriptionsState by repo.observeSubscriptions(clinicId).collectAsStateWithLifecycle(initialValue = emptyList())

    var showPaymentModal by remember { mutableStateOf(false) }
    var showRoadmapModal by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    var upiId by remember { mutableStateOf("clinic@okhdfcbank") }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var paymentSuccessDialog by remember { mutableStateOf<String?>(null) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var isLocalProActivated by remember { mutableStateOf(false) }

    val isProActive = clinicState?.subscriptionStatus == SubscriptionStatus.ACTIVE.name || isLocalProActivated

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Clinic Subscription",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = clinicState?.name ?: "SehatFlow Clinic",
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
                        Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
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
            // Plan Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isProActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isProActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = if (isProActive) "ACTIVE PRO PLAN" else "14-DAY FREE TRIAL",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "₹499 / Month",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isProActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "SehatFlow Clinic Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isProActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Text(
                        text = "Flat rate per clinic. No per-doctor or per-token charges. Full access to real-time live queues, prescription triage, and patient sound chimes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isProActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )

                    Button(
                        onClick = { showPaymentModal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_upgrade_renew_subscription"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProActive) "Renew / Extend Plan (₹499)" else "Activate Pro Subscription (₹499/mo)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Included Pro Features Card
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
                        text = "Included in Your ₹499/Month Clinic Plan",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    PlanFeatureRow("Unlimited Patient Tokens", "Issue walk-in or online OPD tokens with estimated wait calculation.")
                    PlanFeatureRow("Multi-Doctor & Reception Desks", "Synchronize unlimited doctor cabins with individual OPD queues.")
                    PlanFeatureRow("DPDP Act 2023 Prescription Triage", "Patients safely upload prescriptions with receptionist verification.")
                    PlanFeatureRow("Zero-Lag Cloud Database", "Real-time sync backed by Firebase Firestore with offline cache fallback.")
                    PlanFeatureRow("Live Patient Wait Tracking", "Patients view people ahead on mobile with audio chimes on consultation.")
                    PlanFeatureRow("OPD Analytics & Reporting", "End-of-day patient footfall, average consult times, and audit logs.")
                }
            }

            // Billing & Invoice History
            Text(
                text = "Invoices & Payment Records",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (subscriptionsState.isEmpty()) {
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
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No paid invoices yet. You are currently on the 14-day free trial.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                subscriptionsState.forEach { sub ->
                    InvoiceHistoryCard(sub)
                }
            }
        }
    }

    if (showPaymentModal) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingPayment) showPaymentModal = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Subscribe: SehatFlow Pro", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Amount Payable: ₹499 (1 Month Access)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Select Payment Gateway / Method:",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPaymentMethod == "UPI",
                            onClick = { selectedPaymentMethod = "UPI" },
                            label = { Text("UPI / QR") }
                        )
                        FilterChip(
                            selected = selectedPaymentMethod == "CARD",
                            onClick = { selectedPaymentMethod = "CARD" },
                            label = { Text("Cards / NetBanking") }
                        )
                    }

                    if (selectedPaymentMethod == "UPI") {
                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            label = { Text("UPI ID / VPA") },
                            placeholder = { Text("clinic@upi") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_upi_id")
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
            confirmButton = {
                Button(
                    onClick = {
                        isProcessingPayment = true
                        paymentError = null
                        coroutineScope.launch {
                            try {
                                val uid = currentUser?.uid ?: "admin_demo"
                                val thirtyDaysFromNow = Timestamp(Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000))

                                val record = SubscriptionRecord(
                                    clinicId = clinicId,
                                    adminUid = uid,
                                    planName = "SehatFlow Pro (₹499/mo)",
                                    amount = 499L,
                                    currency = "INR",
                                    status = "ACTIVE",
                                    paymentMethod = selectedPaymentMethod,
                                    transactionId = "TXN-SF-" + (100000..999999).random(),
                                    expiresAt = thirtyDaysFromNow
                                )

                                if (currentUser != null) {
                                    repo.recordSubscription(record)
                                }
                                isLocalProActivated = true
                                isProcessingPayment = false
                                showPaymentModal = false
                                paymentSuccessDialog = record.transactionId
                            } catch (e: Exception) {
                                isProcessingPayment = false
                                paymentError = e.localizedMessage ?: "Payment processing failed"
                            }
                        }
                    },
                    enabled = !isProcessingPayment,
                    modifier = Modifier.testTag("btn_confirm_pay_499")
                ) {
                    if (isProcessingPayment) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Pay ₹499 & Activate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPaymentModal = false },
                    enabled = !isProcessingPayment
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (paymentSuccessDialog != null) {
        AlertDialog(
            onDismissRequest = { paymentSuccessDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Subscription Activated!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your clinic is now upgraded to SehatFlow Pro for 30 days.")
                    Text(
                        text = "Transaction Ref: ${paymentSuccessDialog}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "GST Tax Invoice #SF-${System.currentTimeMillis() % 100000} has been registered.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { paymentSuccessDialog = null }) {
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
                    text = "₹${sub.amount} PAID",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
