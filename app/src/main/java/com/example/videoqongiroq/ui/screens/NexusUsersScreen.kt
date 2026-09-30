package com.example.videoqongiroq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoqongiroq.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusUsersScreen(
    currentUser: User?,
    onlineUsers: List<User>,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    onLogout: () -> Unit,
    onStartCall: (User) -> Unit,
    onAddContact: (phone: String, name: String) -> Unit,
    onSimulateIncomingCall: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var newPhoneInput by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }
    var tempServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    val darkBackground = Color(0xFF0B0F19)
    val cardBackground = Color(0xFF161F30)
    val primaryCyan = Color(0xFF00E5FF)
    val neonViolet = Color(0xFF7C4DFF)

    val filteredUsers = remember(onlineUsers, searchQuery) {
        if (searchQuery.isBlank()) onlineUsers
        else onlineUsers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
        }
    }

    Scaffold(
        containerColor = darkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Abonentlar Ro'yxati",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        currentUser?.let {
                            Text(
                                text = "Siz: ${it.name} (${it.formattedPhone})",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Server Sozlamalari",
                            tint = primaryCyan
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Odam qo'shish",
                            tint = primaryCyan
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Chiqish",
                            tint = Color(0xFFFF5252)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121829)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSimulateIncomingCall,
                icon = { Icon(Icons.Default.RingVolume, contentDescription = "Test Call", tint = Color.Black) },
                text = { Text("Kiruvchi call test", color = Color.Black, fontWeight = FontWeight.Bold) },
                containerColor = primaryCyan,
                contentColor = Color.Black
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Ism yoki nomer bo'yicha qidirish...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryCyan) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryCyan,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BARCHA ABONENTLAR (${filteredUsers.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = primaryCyan,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Abonentlar topilmadi. Yuqoridagi '+' tugmasi orqali yangi telefon raqam qo'shing!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredUsers) { user ->
                        NexusUserCard(
                            user = user,
                            onCallClick = { onStartCall(user) }
                        )
                    }
                }
            }
        }
    }

    // Server Settings Dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Cloud Signaling Server", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Real vaqtda vizv uzatish uchun Cloud server manzili:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("WebSocket Server URL") },
                        singleLine = true
                    )
                }
            },
            containerColor = cardBackground,
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateServerUrl(tempServerUrl)
                        showServerDialog = false
                    }
                ) {
                    Text("Saqlash va Ulanish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Bekor qilish", color = Color.Gray)
                }
            }
        )
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Yangi Abonent Qo'shish", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("Ism") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPhoneInput,
                        onValueChange = { newPhoneInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Telefon raqam") },
                        prefix = { Text("+998 ") },
                        singleLine = true
                    )
                }
            },
            containerColor = cardBackground,
            confirmButton = {
                Button(
                    onClick = {
                        if (newPhoneInput.isNotBlank()) {
                            onAddContact("998$newPhoneInput", newNameInput.ifBlank { "Abonent" })
                            newPhoneInput = ""
                            newNameInput = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Qo'shish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Bekor qilish", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun NexusUserCard(
    user: User,
    onCallClick: () -> Unit
) {
    val cardBackground = Color(0xFF161F30)
    val primaryCyan = Color(0xFF00E5FF)
    val neonViolet = Color(0xFF7C4DFF)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Gradient
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(primaryCyan, neonViolet))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(1).uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = user.formattedPhone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Status LED
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(user.status.colorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = user.status.labelUz,
                        fontSize = 12.sp,
                        color = Color(user.status.colorHex),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Green Call Button
            Button(
                onClick = onCallClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Vizv",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VIZV",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color.Black
                )
            }
        }
    }
}
