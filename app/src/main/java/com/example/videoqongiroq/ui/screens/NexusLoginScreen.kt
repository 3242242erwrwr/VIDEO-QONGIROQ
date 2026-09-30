package com.example.videoqongiroq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexusLoginScreen(
    onLoginSuccess: (phone: String, name: String) -> Unit
) {
    var phoneInput by remember { mutableStateOf("901234567") }
    var nameInput by remember { mutableStateOf("Foydalanuvchi") }
    var errorMessage by remember { mutableStateOf("") }

    val darkBackground = Color(0xFF0B0F19)
    val cardBackground = Color(0xFF161F30)
    val primaryCyan = Color(0xFF00E5FF)
    val neonViolet = Color(0xFF7C4DFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        darkBackground,
                        Color(0xFF121829),
                        darkBackground
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(primaryCyan.copy(alpha = 0.6f), neonViolet.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Glowing Header Icon
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(primaryCyan, neonViolet)
                            )
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(cardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Logo",
                            modifier = Modifier.size(42.dp),
                            tint = primaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "NEXUS VIDEO",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Smart Muloqot & Ultra HD Video Call",
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryCyan.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Name Input
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Ismingiz", color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Name", tint = primaryCyan)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryCyan,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Phone Input
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Telefon raqamingiz", color = Color.LightGray) },
                    prefix = { Text("+998 ", fontWeight = FontWeight.Bold, color = primaryCyan) },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = "Phone", tint = primaryCyan)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryCyan,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val cleanPhone = phoneInput.trim()
                        val cleanName = nameInput.trim()
                        if (cleanName.isBlank()) {
                            errorMessage = "Iltimos, ismingizni kiriting"
                        } else if (cleanPhone.length < 7) {
                            errorMessage = "Iltimos, to'g'ri telefon raqam kiriting"
                        } else {
                            errorMessage = ""
                            onLoginSuccess("998$cleanPhone", cleanName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(primaryCyan, neonViolet)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "KIRISH VA ULANISH",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
