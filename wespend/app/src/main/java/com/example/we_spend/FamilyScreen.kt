package com.example.we_spend

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(navController: NavController, viewModel: FamilyViewModel) {
    val context = LocalContext.current
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUserFamily()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zarządzanie rodziną") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
            } else if (viewModel.familyId == null) {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Filled.FamilyRestroom,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Nie należysz jeszcze do żadnej rodziny",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        viewModel.createNewFamily(
                            onSuccess = { Toast.makeText(context, "Rodzina utworzona!", Toast.LENGTH_SHORT).show() },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Utwórz nową rodzinę")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Twoja rodzina", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ID: ${viewModel.familyId}", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Zaproś członka rodziny",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.inviteEmail,
                    onValueChange = { viewModel.updateInviteEmail(it) },
                    label = { Text("Adres e-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.sendInvite(
                                    onSuccess = { Toast.makeText(context, "Wysłano zaproszenie!", Toast.LENGTH_SHORT).show() },
                                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                )
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Członkowie rodziny",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                viewModel.familyMembers.forEach { member ->
                    FamilyMemberItem(
                        user = member,
                        isViewerOwner = viewModel.isOwner,
                        currentUserId = viewModel.currentUserId,
                        familyOwnerId = viewModel.ownerId,
                        onRemoveClick = {
                            viewModel.removeMember(
                                userId = member.uid,
                                onSuccess = { Toast.makeText(context, "Usunięto członka", Toast.LENGTH_SHORT).show() },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showLeaveDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Opuść rodzinę")
                }

                if (showLeaveDialog) {
                    AlertDialog(
                        onDismissRequest = { showLeaveDialog = false },
                        title = { Text("Opuść rodzinę", fontWeight = FontWeight.Bold) },
                        text = { Text("Czy na pewno chcesz opuścić tę rodzinę? Stracisz dostęp do podglądu wspólnych wydatków, ale w każdej chwili będziesz mógł zostać zaproszony ponownie.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showLeaveDialog = false
                                    viewModel.leaveFamily(
                                        onSuccess = { Toast.makeText(context, "Opuszczono rodzinę", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Opuść")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showLeaveDialog = false }) {
                                Text("Anuluj")
                            }
                        }
                    )
                }

                if (viewModel.isOwner) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Usuń rodzinę")
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Usuń rodzinę", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                            text = { Text("Ta akcja jest nieodwracalna. Wszyscy członkowie zostaną odpięci od wspólnych wydatków.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteDialog = false
                                        viewModel.deleteEntireFamily(
                                            onSuccess = { Toast.makeText(context, "Rodzina została usunięta", Toast.LENGTH_SHORT).show() },
                                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Rozwiąż grupę")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FamilyMemberItem(
    user: User,
    isViewerOwner: Boolean,
    currentUserId: String,
    familyOwnerId: String?,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedName = user.name.ifBlank { "Użytkownik" }
            val initial = displayedName.take(1).uppercase()
            val avatarBitmap = decodeBase64Image(user.avatarUrl)

            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "Awatar użytkownika",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name.ifBlank { "Użytkownik" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (user.uid == familyOwnerId) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Założyciel rodziny",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (user.uid == currentUserId) {
                        Text(
                            text = " (Ty)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Text(text = user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isViewerOwner && user.uid != currentUserId && user.uid != familyOwnerId) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń członka", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}