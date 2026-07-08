package com.juicedcooky.notimanager.group

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun NewGroupDialog(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = onGroupNameChange,
                label = { Text("Enter group name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
