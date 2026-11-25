package com.universalstreamhub.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalstreamhub.app.AppGraph
import com.universalstreamhub.app.BuildConfig
import com.universalstreamhub.app.data.remote.PreferencesDto
import com.universalstreamhub.app.ui.components.HubTopBar
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubSurface
import com.universalstreamhub.app.ui.theme.HubSurfaceHigh
import com.universalstreamhub.app.ui.vmFactory
import com.universalstreamhub.app.util.formatStorageLimit

private val GENRES = listOf(
    "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family",
    "Fantasy", "Film-Noir", "History", "Horror", "Music", "Mystery", "Romance", "Sci-Fi",
    "Silent", "Thriller", "War", "Western",
)

@Composable
fun ProfileScreen(onOpenDownloads: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(
        factory = vmFactory {
            ProfileViewModel(AppGraph.container.auth, AppGraph.container.catalog, AppGraph.container.userStore)
        },
    )
    val user by viewModel.user.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setDownloadTree(uri.toString())
        }
    }

    var displayName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var region by remember(settings.region) { mutableStateOf(settings.region) }
    var selectedGenres by remember(settings.favoriteGenres) { mutableStateOf(settings.favoriteGenres.toSet()) }
    var serverUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
    val avatarColors = listOf("#FF4D6D", "#7C5CFF", "#41D3A2", "#FFC857", "#4DA3FF")
    var avatarColor by remember(user?.avatarColor) { mutableStateOf(user?.avatarColor ?: "#FF4D6D") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { HubTopBar() }

            if (!signedIn) {
                item {
                    AuthSection(
                        busy = busy,
                        onLogin = viewModel::login,
                        onRegister = viewModel::register,
                        onForgot = viewModel::forgotPassword,
                        onReset = viewModel::resetPassword,
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(parseColor(avatarColor, HubAccent)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (user?.displayName ?: "U").take(1).uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(user?.displayName ?: "Member", style = MaterialTheme.typography.headlineSmall)
                            Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = HubMuted)
                        }
                    }
                }

                item {
                    SectionCard(title = "Profile") {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(60) },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Avatar colour", style = MaterialTheme.typography.labelLarge, color = HubMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            avatarColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(color, HubAccent))
                                        .border(
                                            width = if (avatarColor == color) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape,
                                        )
                                        .clickable { avatarColor = color },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.saveProfile(
                                    displayName = displayName,
                                    avatarColor = avatarColor,
                                    preferences = PreferencesDto(
                                        favoriteGenres = selectedGenres.toList(),
                                        region = region.uppercase(),
                                        language = settings.language,
                                        storageLimitGb = settings.storageLimitGb,
                                        wifiOnlyDownloads = settings.wifiOnlyDownloads,
                                        autoplayNext = true,
                                    ),
                                )
                                viewModel.saveLocal(region = region, favoriteGenres = selectedGenres.toList())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save profile") }
                    }
                }
            }

            item {
                SectionCard(title = "Taste & recommendations") {
                    Text(
                        "Favourite genres feed your recommendations together with your own watchlist and viewing history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(GENRES) { genre ->
                            FilterChip(
                                selected = genre in selectedGenres,
                                onClick = {
                                    selectedGenres = if (genre in selectedGenres) selectedGenres - genre else selectedGenres + genre
                                    viewModel.saveLocal(favoriteGenres = selectedGenres.toList())
                                },
                                label = { Text(genre) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HubAccent,
                                    selectedLabelColor = HubBlack,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it.uppercase().filter { c -> c.isLetter() }.take(2) },
                        label = { Text("Region code (for provider availability)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Language: ${settings.language}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HubMuted,
                    )
                    TextButton(onClick = { viewModel.saveLocal(region = region) }) {
                        Text("Save region", color = HubAccent)
                    }
                }
            }

            item {
                SectionCard(title = "Offline storage") {
                    Text(
                        "Allowance: ${formatStorageLimit(settings.storageLimitGb)}. Downloads are limited to the free " +
                            "public-domain catalog; subscription titles download inside their own apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(4, 8, 16, 32, 64, -1)) { gb ->
                            FilterChip(
                                selected = settings.storageLimitGb == gb,
                                onClick = {
                                    viewModel.saveLocal(storageLimitGb = gb)
                                    if (signedIn) {
                                        viewModel.saveProfile(
                                            displayName = displayName,
                                            avatarColor = avatarColor,
                                            preferences = PreferencesDto(
                                                favoriteGenres = selectedGenres.toList(),
                                                region = region.uppercase(),
                                                language = settings.language,
                                                storageLimitGb = gb,
                                                wifiOnlyDownloads = settings.wifiOnlyDownloads,
                                            ),
                                        )
                                    }
                                },
                                label = { Text(formatStorageLimit(gb)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HubAccent,
                                    selectedLabelColor = HubBlack,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi only downloads", style = MaterialTheme.typography.bodyMedium)
                            Text("Pause downloads on mobile data", style = MaterialTheme.typography.labelSmall, color = HubMuted)
                        }
                        Switch(
                            checked = settings.wifiOnlyDownloads,
                            onCheckedChange = { viewModel.saveLocal(wifiOnly = it) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { folderPicker.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (settings.downloadTreeUri == null) "Choose SD card folder" else "Change folder")
                        }
                        if (settings.downloadTreeUri != null) {
                            TextButton(onClick = { viewModel.setDownloadTree(null) }) {
                                Text("Reset", color = HubAccent)
                            }
                        }
                    }
                    Button(
                        onClick = onOpenDownloads,
                        colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage downloads")
                    }
                }
            }

            item {
                SectionCard(title = "Hub server") {
                    Text(
                        "The app talks to your own hub API. Add TMDB_API_KEY on the server to enable the full " +
                            "multi-provider catalog; the free public-domain catalog works without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("API base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.testAndSaveServer(serverUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Test & save server") }
                    settings.serverReachable?.let { reachable ->
                        Text(
                            if (reachable) "Server reachable" else "Server unreachable - check the URL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "About & legal") {
                    Text(
                        "Universal Stream Hub ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HubMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Discovery metadata comes from TMDB when configured. Free titles stream from the Internet Archive " +
                            "public-domain catalog. Licensed titles open in the provider's official app; DRM, subscriptions " +
                            "and regional rights are never bypassed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HubMuted,
                    )
                }
            }

            if (signedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::signOut,
                            colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                            modifier = Modifier.weight(1f),
                        ) { Text("Sign out") }
                        Button(
                            onClick = viewModel::deleteAccount,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete account")
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HubSurface)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AuthSection(
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onForgot: (String) -> Unit,
    onReset: (String, String) -> Unit,
) {
    var registering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var recoveryMode by remember { mutableStateOf(false) }
    var resetToken by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    SectionCard(title = if (registering) "Create your account" else "Sign in") {
        Text(
            "Accounts keep your list, progress and preferences in sync across devices. Passwords are hashed with PBKDF2 on the server.",
            style = MaterialTheme.typography.bodySmall,
            color = HubMuted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (registering) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(60) },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 8 characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (registering) onRegister(email, password, displayName) else onLogin(email, password)
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(if (registering) "Create account" else "Sign in")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { registering = !registering }) {
                Text(if (registering) "I already have an account" else "Create an account", color = HubAccent)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { recoveryMode = !recoveryMode }) {
                Text("Forgot password", color = HubMuted)
            }
        }

        if (recoveryMode) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Password recovery sends a reset token by email. The server must have RESEND_API_KEY configured.",
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onForgot(email) },
                colors = ButtonDefaults.buttonColors(containerColor = HubSurfaceHigh),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Email me a reset token") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = resetToken,
                onValueChange = { resetToken = it },
                label = { Text("Reset token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onReset(resetToken, newPassword) },
                colors = ButtonDefaults.buttonColors(containerColor = HubAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set new password") }
        }
    }
}

private fun parseColor(value: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(value))
} catch (_: Exception) {
    fallback
}
