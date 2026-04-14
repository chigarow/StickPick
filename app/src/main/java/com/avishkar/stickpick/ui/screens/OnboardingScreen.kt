package com.avishkar.stickpick.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.avishkar.stickpick.R
import com.avishkar.stickpick.viewmodel.MainViewModel

@Composable
fun OnboardingScreen(vm: MainViewModel, onComplete: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .statusBarsPadding()
        ) {
            Spacer(Modifier.height(60.dp))

            // Logo
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "StickPick",
                    modifier = Modifier.height(40.dp),
                    contentScale = ContentScale.FillHeight
                )
            }

            Spacer(Modifier.height(32.dp))

            // Step indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                },
                label = "step"
            ) { currentStep ->
                Column {
                    when (currentStep) {
                        0 -> {
                            // Welcome + Token
                            Text(
                                "Welcome",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Connect your Telegram bot to start converting sticker packs.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 24.sp
                            )

                            Spacer(Modifier.height(40.dp))

                            Text("Bot Token", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                TextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    placeholder = { Text("Paste token from @BotFather", color = MaterialTheme.colorScheme.outline.copy(0.5f)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Create a bot via @BotFather on Telegram and paste the token here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 18.sp
                            )
                        }
                        1 -> {
                            // Author name
                            Text(
                                "Almost\nthere",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 40.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your name will appear as the publisher on all sticker packs.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 24.sp
                            )

                            Spacer(Modifier.height(40.dp))

                            Text("Your Name", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                TextField(
                                    value = author,
                                    onValueChange = { author = it },
                                    placeholder = { Text("Enter your name", color = MaterialTheme.colorScheme.outline.copy(0.5f)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Action button
            Button(
                onClick = {
                    if (step == 0 && token.isNotBlank()) {
                        step = 1
                    } else if (step == 1 && author.isNotBlank()) {
                        vm.completeOnboarding(token, author, "{name}_by_{author}")
                        onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = if (step == 0) token.isNotBlank() else author.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (step == 0) "Continue" else "Get Started",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
