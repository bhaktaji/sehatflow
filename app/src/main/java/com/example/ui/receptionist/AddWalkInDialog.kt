package com.example.ui.receptionist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AddWalkInDialog(
    onConfirm: (name: String, isPriority: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPriority by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Walk-in Patient") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Patient Full Name") },
                    placeholder = { Text("e.g. Sarah Jenkins") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Patient name cannot be empty") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("walk_in_name_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isPriority,
                        onCheckedChange = { isPriority = it },
                        modifier = Modifier.testTag("walk_in_priority_checkbox")
                    )
                    Text(
                        text = "Emergency / Priority Case",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(name.trim(), isPriority)
                    }
                },
                modifier = Modifier.testTag("walk_in_confirm_btn")
            ) {
                Text(stringResource(R.string.confirm_btn))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("walk_in_cancel_btn")) {
                Text(stringResource(R.string.cancel_btn))
            }
        }
    )
}
