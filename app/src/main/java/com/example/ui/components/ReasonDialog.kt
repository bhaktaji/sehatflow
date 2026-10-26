package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun ReasonDialog(
    title: String,
    confirmLabel: String = stringResource(R.string.confirm_btn),
    descriptionText: String? = null,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (descriptionText != null) {
                    Text(descriptionText)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text(stringResource(R.string.reason_required_hint)) },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Reason is required for the audit trail") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reason_input_field"),
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(reason.trim())
                    }
                },
                modifier = Modifier.testTag("reason_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("reason_cancel_button")
            ) {
                Text(stringResource(R.string.cancel_btn))
            }
        }
    )
}
