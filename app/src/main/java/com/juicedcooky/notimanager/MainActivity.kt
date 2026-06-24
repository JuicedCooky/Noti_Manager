package com.juicedcooky.notimanager

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.juicedcooky.notimanager.ui.theme.NotiManagerTheme

class MainActivity : ComponentActivity() {
    private var hasNotificationAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        enableEdgeToEdge()
        setContent {
            NotiManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasNotificationAccess) {
                        AppBubbleScreen(modifier = Modifier.padding(innerPadding))
                    } else {
                        SettingsScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotificationAccess = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(R.drawable.noti_manager_foreground),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Notification Access Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Text(
            "NotiManager needs permission to read your notifications so it can organize them into the groups you define.",
            style = MaterialTheme.typography.bodyLarge
        )

        HorizontalDivider()

        Text("What this app does with your notifications:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        val bullets = listOf(
            "Reads the title and text of each notification to re-post it inside your chosen group.",
            "Cancels the original notification and replaces it with a grouped version — nothing is hidden without your knowledge.",
            "Stores only your group settings (names, icons, and which apps belong to each group). Notification content is never saved to disk or sent anywhere.",
            "All data stays on your device. No information is shared with third parties or transmitted to any server."
        )
        bullets.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider()

        Text(
            "You can revoke this permission at any time in your device settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Notification Access")
        }
    }
}
