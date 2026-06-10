package com.example.we_spend

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    navController: NavController,
    viewModel: ExpensesViewModel,
    auth: FirebaseAuth,
    sharedPrefs: SharedPreferences
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        viewModel.loadData()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = viewModel.userName,
                avatarUrl = viewModel.avatarUrl,
                currentRoute = "expenses",
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
                Column {
                    TopAppBar(
                        title = { Text("Historia wydatków", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filtruj")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    TabRow(selectedTabIndex = if (viewModel.isFamilyView) 1 else 0) {
                        Tab(
                            selected = !viewModel.isFamilyView,
                            onClick = { viewModel.toggleFamilyView(false) },
                            text = { Text("Moje wydatki") }
                        )
                        Tab(
                            selected = viewModel.isFamilyView,
                            onClick = { viewModel.toggleFamilyView(true) },
                            text = { Text("Wydatki rodziny") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                var expanded by remember { mutableStateOf(false) }
                val periods = listOf(30 to "Ostatnie 30 dni", 60 to "Ostatnie 60 dni", 180 to "Ostatnie pół roku", 360 to "Ostatni rok", null to "Wszystkie dotychczasowe")
                val selectedLabel = periods.find { it.first == viewModel.timePeriodDays }?.second ?: "Wszystkie dotychczasowe"

                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    placeholder = { Text("Szukaj wydatku...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Ikona wyszukiwania")
                    },
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Wyczyść wyszukiwanie")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Okres: $selectedLabel")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        periods.forEach { (days, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setTimePeriod(days)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (viewModel.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (viewModel.filteredExpenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Brak wydatków spełniających kryteria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.filteredExpenses) { expense ->
                            ExpenseListItem(
                                expense = expense,
                                onDeleteClick = { expenseToDelete = expense },
                                onRecurringClick = {
                                    navController.navigate("edit_recurring/${expense.recurringExpenseId}")
                                }
                            )
                        }
                    }
                }
            }

            if (showFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFilterSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Filtrowanie zaawansowane", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Typ wydatku", fontWeight = FontWeight.SemiBold)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setExpenseType("Wszystkie") }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = viewModel.selectedExpenseType == "Wszystkie",
                                    onClick = { viewModel.setExpenseType("Wszystkie") }
                                )
                                Text("Wszystkie")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setExpenseType("Jednorazowy") }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = viewModel.selectedExpenseType == "Jednorazowy",
                                    onClick = { viewModel.setExpenseType("Jednorazowy") }
                                )
                                Text("Jednorazowe")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setExpenseType("Stały") }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = viewModel.selectedExpenseType == "Stały",
                                    onClick = { viewModel.setExpenseType("Stały") }
                                )
                                Text("Stałe")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Kategorie", fontWeight = FontWeight.SemiBold)
                        viewModel.availableCategories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleCategory(category) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = viewModel.selectedCategories.contains(category),
                                    onCheckedChange = { viewModel.toggleCategory(category) }
                                )
                                Text(category)
                            }
                        }

                        if (viewModel.isFamilyView && viewModel.familyMembers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Wydatki członków rodziny", fontWeight = FontWeight.SemiBold)
                            viewModel.familyMembers.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleUserFilter(member.uid) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = viewModel.selectedUsers.contains(member.uid),
                                        onCheckedChange = { viewModel.toggleUserFilter(member.uid) }
                                    )
                                    Text(member.name.ifBlank { member.email })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        if (expenseToDelete != null) {
            AlertDialog(
                onDismissRequest = { expenseToDelete = null },
                title = { Text("Usuń wydatek", fontWeight = FontWeight.Bold) },
                text = { Text("Czy na pewno chcesz usunąć ten wydatek? Ta akcja jest nieodwracalna.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val exp = expenseToDelete
                            expenseToDelete = null
                            if (exp != null) {
                                viewModel.deleteExpense(
                                    expense = exp,
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, "Usunięto wydatek", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        android.widget.Toast.makeText(context, "Błąd: $it", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Usuń")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { expenseToDelete = null }) {
                        Text("Anuluj")
                    }
                }
            )
        }
    }
}