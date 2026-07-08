package com.juicedcooky.notimanager.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search apps") },
        singleLine = true,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        trailingIcon = if (query.isNotEmpty()) {
            { Text("✕", modifier = Modifier.clickable { onQueryChange("") }.padding(4.dp)) }
        } else null
    )
}
