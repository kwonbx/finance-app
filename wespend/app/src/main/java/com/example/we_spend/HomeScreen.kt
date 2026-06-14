package com.example.we_spend

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.navigation.compose.currentBackStackEntryAsState
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
    val totalBalance = viewModel.monthlyRevenue - viewModel.monthlyTotal
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.destination?.route == "home") {
            viewModel.loadData()
        }
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
                    sharedPrefs.edit { 
                        putBoolean("REMEMBER_ME", false)
                        putString("THEME_MODE", "system")
                    }
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
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { navController.navigate("add_revenue") },
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Dodaj przychód")
                    }
                    FloatingActionButton(
                        onClick = { navController.navigate("add_expense") },
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Dodaj wydatek")
                    }
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
                            Text(text = "Bilans w tym miesiącu", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format("%.2f zł", totalBalance),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalBalance >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(text = "Przychody", style = MaterialTheme.typography.bodySmall)
                                    Text(text = String.format("+%.2f zł", viewModel.monthlyRevenue), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(text = "Wydatki", style = MaterialTheme.typography.bodySmall)
                                    Text(text = String.format("-%.2f zł", viewModel.monthlyTotal), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

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
                                        color = MaterialTheme.colorScheme.tertiary
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
                            Text(text = "Wydatki (ostatnie 7 dni):", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%.2f zł", viewModel.weeklyTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    var selectedTab by remember { mutableStateOf(0) }
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("Wydatki", modifier = Modifier.padding(8.dp))
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Text("Przychody", modifier = Modifier.padding(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedTab == 0) {
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
                                    ExpenseListItem(
                                        expense = expense,
                                        onClick = {
                                            navController.navigate("edit_expense/${expense.id}")
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        if (viewModel.recentRevenues.isEmpty()) {
                            Text(
                                text = "Brak przychodów w tym miesiącu.\nNaciśnij przycisk +, aby dodać.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(viewModel.recentRevenues) { revenue ->
                                    RevenueListItem(
                                        revenue = revenue,
                                        onClick = {
                                            navController.navigate("edit_revenue/${revenue.id}")
                                        }
                                    )
                                }
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
fun ExpenseListItem(
    expense: Expense,
    onDeleteClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dateString = sdf.format(Date(expense.dateInMillis))

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isMyExpense = currentUserId == expense.userId

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) {
            onClick?.invoke()
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = expense.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    if (expense.shopName.isNotBlank()) {
                        Text(
                            text = expense.shopName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = String.format("-%.2f zł", expense.amount),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp
                )
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = expense.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = expense.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isMyExpense && onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Usuń",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RevenueListItem(
    revenue: Revenue,
    onDeleteClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dateString = sdf.format(Date(revenue.dateInMillis))

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isMyRevenue = currentUserId == revenue.userId

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) {
            onClick?.invoke()
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = revenue.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Text(
                    text = String.format("+%.2f zł", revenue.amount),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 16.sp
                )
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = revenue.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = revenue.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isMyRevenue && onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Usuń",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
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

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Historia przychodów") },
            selected = currentRoute == "revenues",
            onClick = {
                onClose()
                if (currentRoute != "revenues") onNavigate("revenues")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Analityka") },
            selected = currentRoute == "analytics",
            onClick = {
                onClose()
                if (currentRoute != "analytics") onNavigate("analytics")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Mapa wydatków") },
            selected = currentRoute == "map",
            onClick = {
                onClose()
                if (currentRoute != "map") onNavigate("map")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Lista zakupów") },
            selected = currentRoute == "shopping_list",
            onClick = {
                onClose()
                if (currentRoute != "shopping_list") onNavigate("shopping_list")
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        NavigationDrawerItem(
            label = { Text("Cele oszczędnościowe") },
            selected = currentRoute == "saving_goals",
            onClick = {
                onClose()
                if (currentRoute != "saving_goals") onNavigate("saving_goals")
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