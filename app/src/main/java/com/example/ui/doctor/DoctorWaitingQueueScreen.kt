package com.example.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.QueueToken
import com.example.ui.components.StatusBadge
import com.example.ui.components.ZoomablePrescriptionViewer
import com.example.ui.theme.RedError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorWaitingQueueScreen(
    viewModel: DoctorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waiting Queue (${uiState.waitingTokens.size})", fontWeight = FontWeight.Bold) },
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
        if (uiState.waitingTokens.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No patients currently waiting in queue.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("doctor_waiting_list")
            ) {
                items(uiState.waitingTokens) { token ->
                    DoctorWaitingTokenCard(
                        token = token,
                        onViewRx = { viewModel.viewVerifiedPrescription(token) }
                    )
                }
            }
        }

        // Prescription inspection sheet
        val rxUrl = uiState.selectedPrescriptionUrl
        if (rxUrl != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissPrescriptionModal() },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prescription Document • ${uiState.selectedPrescriptionTokenLabel ?: ""}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { viewModel.dismissPrescriptionModal() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ZoomablePrescriptionViewer(imageUrl = rxUrl)
                }
            }
        }
    }
}

@Composable
private fun DoctorWaitingTokenCard(
    token: QueueToken,
    onViewRx: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("doctor_token_${token.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = token.label,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    if (token.priority) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMERGENCY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RedError,
                            modifier = Modifier
                                .background(RedError.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Patient: ${token.displayName ?: "Walk-in"} • Pos #${token.queuePosition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (token.prescriptionId != null) {
                IconButton(onClick = onViewRx, modifier = Modifier.testTag("inspect_rx_btn_${token.id}")) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "View Prescription",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                StatusBadge(status = token.status)
            }
        }
    }
}
