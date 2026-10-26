package com.example.ui.patient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.TokenStatus
import com.example.ui.components.GlanceableTokenCard
import com.example.ui.components.OfflineBanner
import com.example.ui.components.ReasonDialog
import com.example.ui.theme.getErrorBannerColors
import com.example.ui.theme.getSuccessBannerColors
import com.example.ui.theme.getWarningBannerColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientLiveQueueScreen(
    viewModel: PatientViewModel,
    tokenId: String,
    sessionId: String,
    onReuploadPrescription: (clinicId: String, doctorId: String) -> Unit,
    onViewHistory: () -> Unit,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId, tokenId) {
        viewModel.startRealtimeQueueSync(sessionId, tokenId)
    }

    val token = uiState.activeToken
    val waitMinutes = uiState.calculatedWaitMinutes

    if (showLogoutConfirmDialog && onLogout != null) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            modifier = Modifier.testTag("patient_logout_confirm_dialog"),
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of your patient session?") },
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
                title = { Text("Live Queue Monitor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("live_queue_back_btn")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onViewHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Past Visits")
                    }
                    if (onLogout != null) {
                        IconButton(
                            onClick = { showLogoutConfirmDialog = true },
                            modifier = Modifier.testTag("patient_live_logout_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Log out"
                            )
                        }
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OfflineBanner(isOffline = uiState.isOffline)

            val warningColors = getWarningBannerColors()
            val successColors = getSuccessBannerColors()
            val errorColors = getErrorBannerColors()

            // Alert 1: Optimizing for patient waiting at home: "About 10 minutes remain"
            val show10MinAlert = token?.status == TokenStatus.WAITING &&
                    waitMinutes != null && waitMinutes in 1..10

            AnimatedVisibility(visible = show10MinAlert) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("departure_alert_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = warningColors.container,
                        contentColor = warningColors.content
                    ),
                    border = BorderStroke(1.dp, warningColors.border),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = warningColors.icon,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Time to head out!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = warningColors.content
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "About $waitMinutes minutes remain before your turn. Please start traveling to the clinic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = warningColors.content
                            )
                        }
                    }
                }
            }

            // Alert 2: "You are next!"
            val showNextAlert = token?.status == TokenStatus.WAITING && uiState.peopleAhead == 0
            AnimatedVisibility(visible = showNextAlert) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("you_are_next_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = successColors.container,
                        contentColor = successColors.content
                    ),
                    border = BorderStroke(1.dp, successColors.border),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = successColors.icon,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.you_are_next),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = successColors.content
                        )
                    }
                }
            }

            // Alert 3: "Your turn now / Called"
            val showCalledAlert = token?.status == TokenStatus.CALLED || token?.status == TokenStatus.IN_CONSULTATION
            AnimatedVisibility(visible = showCalledAlert) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("your_turn_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = successColors.container,
                        contentColor = successColors.content
                    ),
                    border = BorderStroke(1.dp, successColors.border),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = successColors.icon,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Your turn now! Please proceed directly into the doctor's room.",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = successColors.content
                        )
                    }
                }
            }

            // Rejection Banner (Prescription needs attention)
            if (token?.status == TokenStatus.REJECTED) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("rejected_alert_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = errorColors.container,
                        contentColor = errorColors.content
                    ),
                    border = BorderStroke(1.dp, errorColors.border),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = errorColors.icon
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Prescription Rejected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = errorColors.content
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reason: ${token.rejectionReason ?: "Illegible or invalid prescription"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = errorColors.content
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val session = uiState.selectedSession
                                if (session != null) {
                                    onReuploadPrescription(session.clinicId, session.doctorId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = errorColors.icon,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("reupload_prescription_btn")
                        ) {
                            Text("Re-upload Prescription")
                        }
                    }
                }
            }

            // Main Glanceable Token & ETA Card
            if (token != null) {
                GlanceableTokenCard(
                    token = token,
                    peopleAhead = uiState.peopleAhead,
                    nowServingLabel = uiState.nowServingToken?.label
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Last Updated Timestamp
            val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
            val formattedTime = remember(uiState.lastUpdatedEpochMs) {
                timeFormatter.format(Date(uiState.lastUpdatedEpochMs))
            }
            Text(
                text = stringResource(R.string.last_updated, formattedTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel Token Action (only active when not terminal)
            if (token != null && !token.status.isTerminal) {
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_token_button")
                ) {
                    Text(stringResource(R.string.cancel_token_btn))
                }
            }
        }

        if (showCancelDialog) {
            ReasonDialog(
                title = "Cancel Queue Token",
                confirmLabel = "Cancel Token",
                descriptionText = "Are you sure you want to give up your spot in line? A reason is required.",
                onConfirm = { reason ->
                    viewModel.cancelActiveToken(reason)
                    showCancelDialog = false
                },
                onDismiss = { showCancelDialog = false }
            )
        }
    }
}
