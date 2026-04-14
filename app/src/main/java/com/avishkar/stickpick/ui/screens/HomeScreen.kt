package com.avishkar.stickpick.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avishkar.stickpick.R
import com.avishkar.stickpick.ui.navigation.Routes
import com.avishkar.stickpick.ui.theme.Poppins
import com.avishkar.stickpick.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onNavigate: (String) -> Unit,
    onFetchComplete: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val stickerSet by vm.stickerSet.collectAsState()

    LaunchedEffect(stickerSet) { if (stickerSet != null) onFetchComplete() }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {}
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))

            // Logo
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "StickPick",
                modifier = Modifier.height(36.dp),
                contentScale = ContentScale.FillHeight
            )

            Spacer(Modifier.height(44.dp))

            // Hero — center aligned
            Text(
                "Capture",
                fontFamily = Poppins, fontSize = 48.sp, fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Art",
                    fontFamily = Poppins, fontSize = 48.sp, fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    ".",
                    fontFamily = Poppins, fontSize = 48.sp, fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                    color = Color(0xFFE8456B)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Convert Telegram sticker packs\nfor WhatsApp instantly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // Input
            TextField(
                value = input, onValueChange = { input = it },
                placeholder = {
                    Text("Paste sticker pack link",
                        color = MaterialTheme.colorScheme.outline.copy(0.4f),
                        fontWeight = FontWeight.Medium)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Link, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(0.4f), modifier = Modifier.size(20.dp))
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )

            Spacer(Modifier.height(14.dp))

            // Fetch
            Button(
                onClick = {
                    if (input.isNotBlank()) { vm.resetWorkflow(); vm.fetchStickerSet(input) }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = input.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Fetch Stickers", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.East, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Error
            AnimatedVisibility(visible = error != null) {
                Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(Modifier.weight(1f))

            // Credit
            Text(
                "Built with ❤\uFE0F by Avishkar Patil",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(90.dp))
            }

            GlassBottomNav(
                currentRoute = Routes.HOME,
                onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun GlassBottomNav(currentRoute: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 18.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassNavItem(Icons.Filled.Home, Icons.Outlined.Home, "Home",
                    currentRoute == Routes.HOME) { onNavigate(Routes.HOME) }
                GlassNavItem(Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "Packs",
                    currentRoute == Routes.MY_PACKS) { onNavigate(Routes.MY_PACKS) }
                GlassNavItem(Icons.Filled.Settings, Icons.Outlined.Settings, "Settings",
                    currentRoute == Routes.SETTINGS) { onNavigate(Routes.SETTINGS) }
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    filledIcon: ImageVector, outlinedIcon: ImageVector,
    label: String, selected: Boolean, onClick: () -> Unit
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f, animationSpec = tween(200), label = "bg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * bgAlpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected) filledIcon else outlinedIcon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.size(20.dp)
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                exit = shrinkHorizontally(tween(150)) + fadeOut(tween(150))
            ) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ModernBottomNav(currentRoute: String, onNavigate: (String) -> Unit) {
    GlassBottomNav(currentRoute = currentRoute, onNavigate = onNavigate)
}
