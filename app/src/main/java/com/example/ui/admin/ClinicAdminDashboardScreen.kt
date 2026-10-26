package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.di.ServiceLocator
import com.example.data.model.SubscriptionStatus
import com.example.ui.onboarding.OnboardingRoadmapDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicAdminDashboardScreen(
    clinicId: String,
    onOpenReception: () -> Unit,
    onOpenDoctor: () -> Unit,
    onOpenSubscription: () -> Unit,
    onLogout: () -> Unit,
    onOpenOperationalHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.sehatFlowRepository }
    val clinicState by repo.observeClinic(clinicId).collectAsStateWithLifecycle(initialValue = null)
    val tokensState by repo.observeTokens(clinicId).collectAsStateWithLifecycle(initialValue = emptyList())

    var showRoadmap by remember { mutableStateOf(false) }

    val isProActive = clinicState?.subscriptionStatus == SubscriptionStatus.ACTIVE.name

    val demoTokens by remember { com.example.core.demo.DemoDataProvider.observeTokens("sess_demo_1") }
        .collectAsStateWithLifecycle(initialValue = com.example.core.demo.DemoDataProvider.getTokens())

    val waitingCount = if (tokensState.isNotEmpty()) tokensState.count { it.status == "WAITING" } else demoTokens.count { it.status == com.example.domain.model.TokenStatus.WAITING }
    val inConsultationCount = if (tokensState.isNotEmpty()) tokensState.count { it.status == "IN_CONSULTATION" } else demoTokens.count { it.status == com.example.domain.model.TokenStatus.IN_CONSULTATION || it.status == com.example.domain.model.TokenStatus.CALLED }
    val completedCount = if (tokensState.isNotEmpty()) tokensState.count { it.status == "COMPLETED" } else demoTokens.count { it.status == com.example.domain.model.TokenStatus.COMPLETED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = clinicState?.name ?: "SehatFlow Clinic",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clinic Admin Hub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenOperationalHistory,
                        modifier = Modifier.testTag("btn_admin_top_history")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Operational History")
                    }
                    TextButton(onClick = { showRoadmap = true }, modifier = Modifier.testTag("btn_admin_roadmap")) {
                        Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Roadmap")
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("btn_admin_logout")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Subscription & Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isProActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isProActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = if (isProActive) "ACTIVE PRO (₹499/MO)" else "14-DAY PRO TRIAL",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "₹499 / Month",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isProActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Text(
                        text = "Unlimited tokens, multiple doctor cabins, real-time sync & DPDP patient consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isProActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )

                    Button(
                        onClick = onOpenSubscription,
                        modifier = Modifier.fillMaxWidth().testTag("btn_admin_subscription_settings"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CardMembership, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isProActive) "Subscription & Billing Invoices" else "Activate ₹499/Month Plan")
                    }
                }
            }

            // Staff Onboarding & Invite Codes
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
                        text = "Staff Onboarding & Invite Codes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Share these codes with your doctors and receptionists to link them directly to your clinic queue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    InviteCodeRow(
                        roleLabel = "Doctor Invite Code",
                        code = clinicState?.inviteCodeDoctor?.ifBlank { "DOC-4819" } ?: "DOC-4819",
                        context = context
                    )

                    InviteCodeRow(
                        roleLabel = "Receptionist Invite Code",
                        code = clinicState?.inviteCodeReceptionist?.ifBlank { "REC-9321" } ?: "REC-9321",
                        context = context
                    )
                }
            }

            // Live OPD Metrics
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Today's OPD Queue Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricBox(label = "Waiting", count = waitingCount, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        MetricBox(label = "Consulting", count = inConsultationCount, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                        MetricBox(label = "Completed", count = completedCount, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // Quick Desk Launchers
            Text(
                text = "Live Operations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenReception,
                    modifier = Modifier.weight(1f).testTag("btn_admin_open_reception"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reception Desk")
                }

                OutlinedButton(
                    onClick = onOpenDoctor,
                    modifier = Modifier.weight(1f).testTag("btn_admin_open_doctor"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Doctor Desk")
                }
            }

            OutlinedButton(
                onClick = onOpenOperationalHistory,
                modifier = Modifier.fillMaxWidth().testTag("btn_admin_operational_history"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Operational & Consultation History Reports")
            }
        }
    }

    if (showRoadmap) {
        OnboardingRoadmapDialog(onDismiss = { showRoadmap = false })
    }
}

@Composable
fun InviteCodeRow(
    roleLabel: String,
    code: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = roleLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }

        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SehatFlow Code", code))
                Toast.makeText(context, "$roleLabel copied: $code", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MetricBox(
    label: String,
    count: Int,
    modifier: Modifier,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
