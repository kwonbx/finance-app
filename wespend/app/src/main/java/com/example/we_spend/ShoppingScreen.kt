package com.example.we_spend

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    navController: NavController,
    viewModel: ShoppingViewModel,
    auth: FirebaseAuth,
    sharedPrefs: SharedPreferences
) {
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showAddListDialog by remember { mutableStateOf(false) }
    var showEditListDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShoppingItem?>(null) }
    var newListName by remember { mutableStateOf("") }
    var newListShopName by remember { mutableStateOf("") }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }

    val userRepository = UserRepository()

    LaunchedEffect(Unit) {
        val user = userRepository.getUserProfile()
        userName = user?.name ?: ""
        avatarUrl = user?.avatarUrl ?: ""
        viewModel.loadData()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = userName,
                avatarUrl = avatarUrl,
                currentRoute = "shopping_list",
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
                    title = {
                        Column {
                            Text("Zakupy", fontWeight = FontWeight.Bold)
                            if (viewModel.currentList != null) {
                                Text(viewModel.currentList!!.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        if (viewModel.currentList == null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        } else {
                            IconButton(onClick = { viewModel.unselectList() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                            }
                        }
                    },
                    actions = {
                        if (viewModel.currentList != null) {
                            IconButton(onClick = { 
                                newListName = viewModel.currentList!!.name
                                newListShopName = viewModel.currentList!!.shopName
                                showEditListDialog = true 
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edytuj listę")
                            }

                            var expanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.List, contentDescription = "Zmień listę")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                viewModel.lists.forEach { list ->
                                    DropdownMenuItem(
                                        text = { Text(list.name) },
                                        onClick = {
                                            viewModel.selectList(list)
                                            expanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Wróć do wszystkich list") },
                                    onClick = {
                                        viewModel.unselectList()
                                        expanded = false
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { 
                        if (viewModel.currentList != null) showAddItemDialog = true 
                        else showAddListDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = if (viewModel.currentList != null) "Dodaj produkt" else "Dodaj listę")
                }
            }
        ) { paddingValues ->
            if (viewModel.errorMessage != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Błąd") },
                    text = { Text(viewModel.errorMessage!!) },
                    confirmButton = {
                        Button(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.currentList == null) {
                // DASHBOARD VIEW - LIST OF LISTS
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // Search Bar
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Szukaj listy lub sklepu...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Wyczyść")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    if (viewModel.filteredLists.isEmpty()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                if (viewModel.lists.isEmpty()) {
                                    Text("Nie masz jeszcze żadnych list", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { showAddListDialog = true }, modifier = Modifier.padding(top = 16.dp)) {
                                        Text("Utwórz pierwszą listę")
                                    }
                                } else {
                                    Text("Nie znaleziono list pasujących do wyszukiwania", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                Text(
                                    "Twoje listy zakupów",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(viewModel.filteredLists) { list ->
                                ShoppingListCard(
                                    list = list,
                                    onClick = { viewModel.selectList(list) },
                                    onDeleteClick = { viewModel.deleteList(list) }
                                )
                            }
                        }
                    }
                }
            } else {
                // LIST ITEMS VIEW
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // Item Search Bar
                    var itemSearchQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Szukaj na tej liście...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredItems = if (itemSearchQuery.isBlank()) viewModel.items 
                                        else viewModel.items.filter { it.name.contains(itemSearchQuery, ignoreCase = true) }

                    // Summary Section
                    val totalItems = viewModel.items.size
                    val checkedItems = viewModel.items.count { it.isChecked }
                    val totalPrice = viewModel.items.sumOf { it.price * it.count }
                    val checkedPrice = viewModel.items.filter { it.isChecked }.sumOf { it.price * it.count }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Postęp zakupów",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "$checkedItems z $totalItems produktów",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "W koszyku / Razem",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        String.format(java.util.Locale.getDefault(), "%.2f / %.2f zł", checkedPrice, totalPrice),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (totalItems > 0) {
                                LinearProgressIndicator(
                                    progress = { checkedItems.toFloat() / totalItems.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (viewModel.items.isEmpty()) "Twoja lista jest pusta" else "Nie znaleziono produktów", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val groupedItems = filteredItems.groupBy { it.category }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            groupedItems.forEach { (category, items) ->
                                item {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(items) { item ->
                                    ShoppingListItem(
                                        item = item,
                                        onCheckedChange = { viewModel.toggleItemChecked(item) },
                                        onDeleteClick = { viewModel.deleteItem(item) },
                                        onEditClick = { itemToEdit = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAddItemDialog) {
                ItemEditDialog(
                    viewModel = viewModel,
                    onDismiss = { showAddItemDialog = false },
                    onConfirm = { name, quantity, count, price, category ->
                        viewModel.addItem(name, quantity, count, price, category)
                        showAddItemDialog = false
                    }
                )
            }

            if (itemToEdit != null) {
                ItemEditDialog(
                    item = itemToEdit,
                    viewModel = viewModel,
                    onDismiss = { itemToEdit = null },
                    onConfirm = { name, quantity, count, price, category ->
                        viewModel.updateItem(itemToEdit!!, name, quantity, count, price, category)
                        itemToEdit = null
                    }
                )
            }

            if (showAddListDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showAddListDialog = false
                        newListName = ""
                        newListShopName = ""
                    },
                    title = { Text("Nowa lista zakupów") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newListName,
                                onValueChange = { newListName = it },
                                label = { Text("Nazwa listy (np. Obiad)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newListShopName,
                                onValueChange = { newListShopName = it },
                                label = { Text("Sklep (opcjonalnie)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.addList(newListName, newListShopName)
                                showAddListDialog = false
                                newListName = ""
                                newListShopName = ""
                            },
                            enabled = newListName.isNotBlank()
                        ) {
                            Text("Utwórz")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showAddListDialog = false
                            newListName = ""
                            newListShopName = ""
                        }) {
                            Text("Anuluj")
                        }
                    }
                )
            }

            if (showEditListDialog && viewModel.currentList != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showEditListDialog = false
                        newListName = ""
                        newListShopName = ""
                    },
                    title = { Text("Edytuj listę zakupów") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newListName,
                                onValueChange = { newListName = it },
                                label = { Text("Nazwa listy") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newListShopName,
                                onValueChange = { newListShopName = it },
                                label = { Text("Sklep") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateList(viewModel.currentList!!, newListName, newListShopName)
                                showEditListDialog = false
                                newListName = ""
                                newListShopName = ""
                            },
                            enabled = newListName.isNotBlank()
                        ) {
                            Text("Zapisz")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showEditListDialog = false
                            newListName = ""
                            newListShopName = ""
                        }) {
                            Text("Anuluj")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShoppingListCard(
    list: ShoppingList,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (list.shopName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = list.shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(
                        text = if (list.familyId != null) "Lista rodzinna" else "Lista prywatna",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Usuń listę", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun ItemEditDialog(
    item: ShoppingItem? = null,
    viewModel: ShoppingViewModel? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Double, String) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantity by remember { mutableStateOf(item?.quantity ?: "") }
    var count by remember { mutableStateOf(item?.count ?: 1) }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Inne") }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(name) {
        if (name.length > 2) {
            viewModel?.loadPriceHistory(name)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Dodaj produkt" else "Edytuj produkt") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa produktu") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (viewModel != null && viewModel.priceHistory.isNotEmpty() && name.isNotBlank()) {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if (showHistory) "Ukryj historię cen" else "Pokaż historię cen (${viewModel.priceHistory.size})")
                    }
                    if (showHistory) {
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(viewModel.priceHistory) { histItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(histItem.quantity.ifBlank { "Ilość n/a" }, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            String.format(java.util.Locale.getDefault(), "%.2f zł", histItem.price),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Kategoria", style = MaterialTheme.typography.labelMedium)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        viewModel?.categories?.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Jednostka / Opis (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (count > 1) count-- }) {
                        Icon(Icons.Default.Remove, contentDescription = "Odejmij")
                    }
                    Text(count.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                    IconButton(onClick = { count++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("ilość", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Cena za jednostkę (zł)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    onConfirm(name, quantity, count, p, category)
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (item == null) "Dodaj" else "Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCheckedChange(!item.isChecked) }
            ) {
                Checkbox(
                    checked = item.isChecked, 
                    onCheckedChange = onCheckedChange
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (item.quantity.isNotBlank() || item.count > 1) {
                            val displayText = when {
                                item.quantity.isNotBlank() && item.count > 1 -> "${item.count}x ${item.quantity}"
                                item.quantity.isNotBlank() -> item.quantity
                                item.count > 1 -> "${item.count} szt."
                                else -> ""
                            }
                            if (displayText.isNotBlank()) {
                                Text(text = displayText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.price > 0) Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        if (item.price > 0) {
                            val totalItemPrice = item.price * item.count
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.2f zł", totalItemPrice),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edytuj", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}
