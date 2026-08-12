package com.avishkar.stickpick.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avishkar.stickpick.ui.navigation.Routes
import com.avishkar.stickpick.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val token by vm.botToken.collectAsState()
    val author by vm.authorName.collectAsState()
    val pattern by vm.packNamingPattern.collectAsState()
    val autoSplit by vm.autoSplit.collectAsState()
    val lossless by vm.losslessConversion.collectAsState()
    val packLimit by vm.packLimit.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val waBusiness by vm.whatsappBusiness.collectAsState()

    var editToken by remember(token) { mutableStateOf(token) }
    var editAuthor by remember(author) { mutableStateOf(author) }
    var editPattern by remember(pattern) { mutableStateOf(pattern) }
    var showClearDialog by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    // Auto-save whenever fields change
    LaunchedEffect(editToken) { if (editToken != token && editToken.isNotBlank()) vm.updateSettings(editToken, editAuthor, editPattern, autoSplit, lossless) }
    LaunchedEffect(editAuthor) { if (editAuthor != author) vm.updateSettings(editToken, editAuthor, editPattern, autoSplit, lossless) }
    LaunchedEffect(editPattern) { if (editPattern != pattern) vm.updateSettings(editToken, editAuthor, editPattern, autoSplit, lossless) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {}
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Spacer(Modifier.height(4.dp))

            // Telegram
            SettingsCard(title = "Telegram", icon = Icons.Outlined.SmartToy) {
                Text("Bot Token", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TextField(
                        value = editToken,
                        onValueChange = { editToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (tokenVisible) "Hide" else "Show",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("From @BotFather", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
            }

            // Identity
            SettingsCard(title = "Identity", icon = Icons.Outlined.Person) {
                SettingsInput(label = "Author Name", value = editAuthor, onValueChange = { editAuthor = it })
                Spacer(Modifier.height(12.dp))
                SettingsInput(label = "Pack Naming", value = editPattern, onValueChange = { editPattern = it },
                    hint = "{name}, {author}, {index}")
            }

            // Appearance
            SettingsCard(title = "Appearance", icon = Icons.Outlined.Palette) {
                Text("Theme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("Light", "light", themeMode == "light", Icons.Outlined.LightMode) { vm.updateThemeMode("light") }
                    ThemeChip("Dark", "dark", themeMode == "dark", Icons.Outlined.DarkMode) { vm.updateThemeMode("dark") }
                    ThemeChip("System", "system", themeMode == "system", Icons.Outlined.SettingsBrightness) { vm.updateThemeMode("system") }
                }
            }

            // Conversion
            SettingsCard(title = "Conversion", icon = Icons.Outlined.Tune) {
                SettingsToggleRow(
                    title = "Auto-split packs",
                    subtitle = "Split after limit reached",
                    checked = autoSplit,
                    onCheckedChange = { vm.updateSettings(editToken, editAuthor, editPattern, it, lossless) }
                )
                Spacer(Modifier.height(12.dp))
                // Pack limit slider
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pack limit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("$packLimit stickers", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = packLimit.toFloat(),
                        onValueChange = { vm.updatePackLimit(it.toInt()) },
                        valueRange = 3f..50f,
                        steps = 46
                    )
                    Text("WhatsApp supports up to 50 per pack", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Lossless mode",
                    subtitle = "Higher quality, larger files",
                    checked = lossless,
                    onCheckedChange = { vm.updateSettings(editToken, editAuthor, editPattern, autoSplit, it) }
                )
            }

            // Backup & Restore
            val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null) vm.exportBackup(uri)
            }

            val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) vm.inspectBackup(uri)
            }

            val importPreview by vm.importPreviewState.collectAsState()
            val isBackupProcessing by vm.isBackupProcessing.collectAsState()
            val backupMessage by vm.backupMessage.collectAsState()

            LaunchedEffect(backupMessage) {
                if (backupMessage != null) {
                    // Handled by snackbar or toast
                }
            }

            SettingsCard(title = "Backup & Restore", icon = Icons.Outlined.Archive) {
                Text("Export or import all sticker packs in standard .sbspk backup format.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("stickpick-backup-${System.currentTimeMillis()}.sbspk") },
                        modifier = Modifier.weight(1f),
                        enabled = !isBackupProcessing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !isBackupProcessing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }

            // WhatsApp
            SettingsCard(title = "WhatsApp", icon = Icons.Outlined.Forum) {
                SettingsToggleRow(
                    title = "WhatsApp Business",
                    subtitle = "Show separate button for WA Business",
                    checked = waBusiness,
                    onCheckedChange = { vm.updateWhatsAppBusiness(it) }
                )
            }

            // Danger
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Clear All Data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }

            // Credit
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("StickPick v1.0", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Built with ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("❤️", fontSize = 12.sp)
                    Text(" by Avishkar Patil", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(80.dp))
            }
            GlassBottomNav(currentRoute = Routes.SETTINGS, onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will delete all packs and cached files. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    val importPreviewState by vm.importPreviewState.collectAsState()
    val previewState = importPreviewState
    if (previewState != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissImportPreview() },
            title = { Text("Import Sticker Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detected ${previewState.importedPacks.size} sticker pack(s) in backup archive:")
                    previewState.importedPacks.forEach { pack ->
                        Text("• ${pack.name} (${pack.stickers.size} stickers)", fontWeight = FontWeight.Medium)
                    }
                    if (previewState.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Warnings / Notes:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        previewState.warnings.forEach { warn ->
                            Text("⚠️ $warn", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Choose Import Mode:", fontWeight = FontWeight.SemiBold)
                    Text("• Merge: Preserves existing stickers and appends new ones (safest).\n• Overwrite: Replaces matching existing packs completely.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirmImport(com.avishkar.stickpick.data.backup.ImportMode.MERGE) }) {
                    Text("Merge All (Safe)")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.confirmImport(com.avishkar.stickpick.data.backup.ImportMode.OVERWRITE) }) {
                        Text("Overwrite", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.dismissImportPreview() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsInput(
    label: String, value: String, onValueChange: (String) -> Unit, hint: String? = null
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            singleLine = true
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowScope.ThemeChip(
    label: String,
    value: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
        else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
