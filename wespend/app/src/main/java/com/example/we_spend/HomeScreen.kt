package com.example.we_spend

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel, auth: FirebaseAuth, sharedPrefs: SharedPreferences) {
    val remainingLimit = viewModel.monthlyLimit - viewModel.monthlyTotal
    val isOverLimit = remainingLimit < 0
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = viewModel.userName,
                avatarUrl = viewModel.avatarUrl,
                currentRoute = "home",
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    sharedPrefs.edit { putBoolean("REMEMBER_ME", false) }
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Twoje podsumowanie", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate("add_expense") },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Dodaj wydatek")
                }
            }
        ) { paddingValues ->

            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Ten miesiąc", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format("%.2f zł", viewModel.monthlyTotal),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (viewModel.monthlyLimit > 0) {
                                if (isOverLimit) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Przekroczono limit o ${String.format("%.2f", kotlin.math.abs(remainingLimit))} zł!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Pozostało z limitu: ${String.format("%.2f", remainingLimit)} zł",
                                        color = Color(0xFF388E3C)
                                    )
                                }
                            } else {
                                Text("Brak ustalonego limitu.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Ostatnie 7 dni:", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%.2f zł", viewModel.weeklyTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Ostatnie wydatki", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (viewModel.recentExpenses.isEmpty()) {
                        Text(
                            text = "Brak wydatków w tym miesiącu.\nNaciśnij przycisk +, aby dodać.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(viewModel.recentExpenses) { expense ->
                                ExpenseListItem(expense = expense)
                            }
                        }
                    }
                }
            }
            if (viewModel.pendingInvitations.isNotEmpty()) {
                val invitation = viewModel.pendingInvitations.first()

                AlertDialog(
                    onDismissRequest = {  },
                    title = { Text("Nowe zaproszenie") },
                    text = { Text("Zostałeś zaproszony do dołączenia do wspólnych wydatków (Rodzina: ${invitation.familyId}). Czy chcesz dołączyć?") },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.respondToInvite(invitation, true) }
                        ) {
                            Text("Akceptuj")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { viewModel.respondToInvite(invitation, false) }
                        ) {
                            Text("Odrzuć")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExpenseListItem(expense: Expense) {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dateString = sdf.format(Date(expense.dateInMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = expense.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = expense.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = String.format("-%.2f zł", expense.amount),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun AppDrawerContent(
    userName: String,
    avatarUrl: String,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedName = userName.ifBlank { "Użytkownik" }
            val initial = displayedName.take(1).uppercase()
            val avatarBitmap = decodeBase64Image(avatarUrl)

            if (avatarBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = avatarBitmap,
                    contentDescription = "Awatar użytkownika",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
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

            Text(
                text = displayedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Zamknij menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Twoje podsumowanie") },
            selected = currentRoute == "home",
            onClick = {
                onClose()
                if (currentRoute != "home") onNavigate("home")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Historia wydatków") },
            selected = currentRoute == "expenses",
            onClick = {
                onClose()
                if (currentRoute != "expenses") onNavigate("expenses")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = {
                    onClose()
                    onNavigate("settings")
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            ) {
                Text("Ustawienia")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            ) {
                Text("Wyloguj się")
            }
        }
    }
}