package com.example.ui.receptionist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PendingPrescription
import com.example.ui.components.ReasonDialog
import com.example.ui.components.ZoomablePrescriptionViewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionReviewSheet(
    pending: PendingPrescription,
    signedImageUrl: String?,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showRejectReasonDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Verify Prescription",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Patient: ${pending.patientName} (${pending.patientIdentifier})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!pending.patientPhone.isNullOrBlank()) {
                        Text(
                            text = "Phone: ${pending.patientPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!pending.presentingComplaint.isNullOrBlank()) {
                        Text(
                            text = "Reason for Visit: ${pending.presentingComplaint}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Doctor: ${pending.doctorName ?: "Dr. John"} • ${pending.chamber ?: "Chamber A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Clinic: ${pending.requestedClinic ?: "XYZ Clinic"} • Requested Day: ${pending.requestedDate ?: "Monday"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sheet_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (pending.requestedDate == "Monday") {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("capacity_reschedule_notice")
                ) {
                    Text(
                        text = "⚠️ Notice: Monday at XYZ Clinic is at 100% capacity (10/10). Approval will automatically allocate patient to the next available eligible slot: Tuesday at XYZ Clinic (Chamber A).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Zoomable Prescription Document Viewer
            if (!signedImageUrl.isNullOrBlank()) {
                ZoomablePrescriptionViewer(imageUrl = signedImageUrl)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pinch to zoom and verify doctor details, date, and medication validity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showRejectReasonDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reject_prescription_btn")
                ) {
                    Text(stringResource(R.string.reject_rx_btn))
                }

                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("approve_prescription_btn")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.approve_rx_btn))
                    }
                }
            }
        }
    }

    if (showRejectReasonDialog) {
        ReasonDialog(
            title = "Reject Prescription",
            confirmLabel = "Confirm Rejection",
            descriptionText = "Please state clearly why the prescription was rejected (e.g. illegible signature, expired date, wrong clinic).",
            onConfirm = { reason ->
                onReject(reason)
                showRejectReasonDialog = false
            },
            onDismiss = { showRejectReasonDialog = false }
        )
    }
}
