package dev.vishnu.wakawidget

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.vishnu.wakawidget.widget.WakaWidgetReceiver
import dev.vishnu.wakawidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ApiKeyStore.load(this) != null) RefreshWorker.schedule(this)
        setContent { AppTheme { SettingsScreen() } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(ApiKeyStore.load(context).orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("WakaTime Widget", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Paste your WakaTime API key or a personal OAuth token. It stays on this " +
                    "phone, encrypted, and is only sent to wakatime.com.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "API key: today's time, languages and projects. " +
                    "OAuth token: the same, plus the 7-day chart.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wakatime.com/settings/api-key"))
                )
            }) { Text("Get your API key") }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wakatime.com/oauth"))
                )
            }) { Text("Get a personal OAuth token (for the 7-day chart)") }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("API key or OAuth token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = !busy && apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    status = "Checking the key with WakaTime…"
                    scope.launch {
                        val key = apiKey
                        val result = runCatching { withContext(Dispatchers.IO) { WakaApi.fetchStats(key) } }
                        result.onSuccess { stats ->
                            ApiKeyStore.save(context, key)
                            StatsCache.save(context, stats)
                            WidgetUpdater.push(context, stats, null)
                            RefreshWorker.schedule(context)
                            val today = "Saved. Today so far: ${formatDuration(stats.todaySeconds)}."
                            status = if (stats.week.isEmpty() && !WakaApi.isOAuthToken(key)) {
                                "$today Use an OAuth token to also see the 7-day chart."
                            } else {
                                today
                            }
                        }
                        result.onFailure { e ->
                            status = if (e is AuthException) {
                                "WakaTime rejected this key or token. Copy it again from WakaTime."
                            } else {
                                "Couldn't reach WakaTime (${e.message}). Check your connection and try again."
                            }
                        }
                        busy = false
                    }
                },
            ) { Text("Save") }

            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val pinned = GlanceAppWidgetManager(context)
                            .requestPinGlanceAppWidget(WakaWidgetReceiver::class.java)
                        if (!pinned) {
                            status = "Your launcher can't add it from here. Long-press the " +
                                "home screen, open Widgets and find WakaTime."
                        }
                    }
                },
            ) { Text("Add widget to home screen") }

            Text(
                "The widget refreshes about every 30 minutes. Tap ↻ on it to refresh now. " +
                    "Resize it to see more: today's time, then top languages, then the last 7 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
