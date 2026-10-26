package com.example.ui.receptionist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.QueueToken

@Composable
fun ReorderQueueDialog(
    token: QueueToken,
    maxPosition: Int,
    onConfirm: (targetPos: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var positionStr by remember { mutableStateOf(token.queuePosition.toString()) }
    var reason by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder Token ${token.label}") },
        text = {
            Column {
                Text(
                    text = "Current Position: #${token.queuePosition} (1 to $maxPosition)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = positionStr,
                    onValueChange = { positionStr = it },
                    label = { Text("Target Position (Number)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("target_position_field")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Reason for Reordering (Mandatory)") },
                    placeholder = { Text("e.g. Urgent lab tests completed") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Reason is mandatory for the audit log") }
                    } else null,
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("reorder_reason_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pos = positionStr.toIntOrNull()
                    if (pos == null || pos < 1 || reason.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(pos, reason.trim())
                    }
                },
                modifier = Modifier.testTag("reorder_confirm_btn")
            ) {
                Text(stringResource(R.string.confirm_btn))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("reorder_cancel_btn")) {
                Text(stringResource(R.string.cancel_btn))
            }
        }
    )
}
