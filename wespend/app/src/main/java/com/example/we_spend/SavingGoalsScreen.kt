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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalsScreen(
    navController: NavController,
    viewModel: SavingGoalViewModel,
    auth: FirebaseAuth,
    sharedPrefs: SharedPreferences
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf<List<SavingStep>>(emptyList()) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }

    val userRepository = UserRepository()

    LaunchedEffect(Unit) {
        val user = userRepository.getUserProfile()
        userName = user?.name ?: ""
        avatarUrl = user?.avatarUrl ?: ""
        viewModel.loadGoals()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                userName = userName,
                avatarUrl = avatarUrl,
                currentRoute = "saving_goals",
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
                    title = { Text("Cele oszczędnościowe", fontWeight = FontWeight.Bold) },
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
                    onClick = { 
                        goalName = ""
                        targetAmount = ""
                        steps = emptyList()
                        showAddDialog = true 
                    },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj cel")
                }
            }
        ) { paddingValues ->
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.goals.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Nie masz jeszcze żadnych celów", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    val userGoals = viewModel.goals.filter { it.familyId == null }
                    val familyGoals = viewModel.goals.filter { it.familyId != null }

                    if (userGoals.isNotEmpty()) {
                        item {
                            Text("Prywatne", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        items(userGoals) { goal ->
                            SavingGoalItem(
                                goal = goal,
                                viewModel = viewModel,
                                onDeleteClick = { viewModel.deleteGoal(goal) }
                            )
                        }
                    }

                    if (familyGoals.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Wspólne", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        items(familyGoals) { goal ->
                            SavingGoalItem(
                                goal = goal,
                                viewModel = viewModel,
                                onDeleteClick = { viewModel.deleteGoal(goal) }
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                GoalEditDialog(
                    title = "Nowy cel oszczędnościowy",
                    initialName = goalName,
                    initialTarget = targetAmount,
                    initialSteps = steps,
                    canShare = viewModel.familyId != null,
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, target, newSteps, isShared ->
                        viewModel.addGoal(name, target, null, newSteps, isShared)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun GoalEditDialog(
    title: String,
    initialName: String,
    initialTarget: String,
    initialSteps: List<SavingStep>,
    isSharedInitially: Boolean = false,
    canShare: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, List<SavingStep>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var target by remember { mutableStateOf(initialTarget) }
    var steps by remember { mutableStateOf(initialSteps) }
    var isShared by remember { mutableStateOf(isSharedInitially) }
    
    var stepName by remember { mutableStateOf("") }
    var stepTarget by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa celu") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Kwota docelowa") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )

                if (canShare) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isShared = !isShared },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isShared, onCheckedChange = { isShared = it })
                        Text("Udostępnij rodzinie", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Kroki / Kamienie milowe", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${step.targetAmount} zł", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                            Icon(Icons.Default.Close, contentDescription = "Usuń krok", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = stepName,
                            onValueChange = { stepName = it },
                            label = { Text("Nazwa kroku") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = stepTarget,
                            onValueChange = { stepTarget = it },
                            label = { Text("Kwota") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = {
                            val amount = stepTarget.toDoubleOrNull() ?: 0.0
                            if (stepName.isNotBlank() && amount > 0) {
                                steps = steps + SavingStep(name = stepName, targetAmount = amount)
                                stepName = ""
                                stepTarget = ""
                            }
                        },
                        enabled = stepName.isNotBlank() && stepTarget.toDoubleOrNull() != null
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Dodaj krok")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = target.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && amount > 0) {
                        onConfirm(name, amount, steps, isShared)
                    }
                },
                enabled = name.isNotBlank() && target.toDoubleOrNull() != null
            ) {
                Text("Zapisz")
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
fun SavingGoalItem(
    goal: SavingGoal,
    viewModel: SavingGoalViewModel,
    onDeleteClick: () -> Unit
) {
    var showTransactionDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pl", "PL"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (goal.familyId != null) {
                        Text(
                            "Wspólny cel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edytuj cel", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete, 
                            contentDescription = "Usuń cel", 
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                    color = if (progress >= 1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )

                // Render markers for steps
                if (goal.targetAmount > 0) {
                    goal.steps.forEach { step ->
                        val stepRatio = (step.targetAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                        val isReached = goal.currentAmount >= step.targetAmount
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(stepRatio)
                                .height(12.dp)
                                .padding(end = 2.dp), // Adjust slightly to not be exactly at the edge
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isReached) Color.White.copy(alpha = 0.9f) 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Aktualnie", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = currencyFormat.format(goal.currentAmount),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Cel", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = currencyFormat.format(goal.targetAmount),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (goal.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showSteps = !showSteps },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (showSteps) "Ukryj kroki" else "Pokaż kroki (${goal.steps.size})",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Icon(
                        if (showSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                if (showSteps) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        goal.steps.forEach { step ->
                            val isStepReached = goal.currentAmount >= step.targetAmount
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isStepReached) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isStepReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = step.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isStepReached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f zł", step.targetAmount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showTransactionDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Wpłać/Wypłać")
                }
                OutlinedButton(
                    onClick = { 
                        showHistory = !showHistory
                        if (showHistory) viewModel.loadTransactions(goal)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(if (showHistory) Icons.Default.ExpandLess else Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Historia")
                }
            }

            if (showHistory) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                val history = viewModel.transactionsByGoal[goal.id] ?: emptyList()
                
                if (history.isEmpty()) {
                    Text("Brak operacji", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                } else {
                    val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        history.take(5).forEach { trans ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trans.note.ifBlank { if (trans.amount > 0) "Wpłata" else "Wypłata" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(sdf.format(Date(trans.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = (if (trans.amount > 0) "+" else "") + String.format(Locale.getDefault(), "%.2f zł", trans.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (trans.amount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTransactionDialog) {
            var transAmount by remember { mutableStateOf("") }
            var transNote by remember { mutableStateOf("") }
            var isWithdrawal by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showTransactionDialog = false },
                title = { Text(if (isWithdrawal) "Wypłać środki" else "Wpłać środki") },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !isWithdrawal, onClick = { isWithdrawal = false })
                            Text("Wpłata (+)", modifier = Modifier.clickable { isWithdrawal = false })
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(selected = isWithdrawal, onClick = { isWithdrawal = true })
                            Text("Wypłata (-)", modifier = Modifier.clickable { isWithdrawal = true })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = transAmount,
                            onValueChange = { transAmount = it },
                            label = { Text("Kwota") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = transNote,
                            onValueChange = { transNote = it },
                            label = { Text("Notatka (np. Z premii)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val amount = transAmount.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            val finalAmount = if (isWithdrawal) -amount else amount
                            viewModel.addTransaction(goal, finalAmount, transNote)
                            showTransactionDialog = false
                        }
                    }) {
                        Text("Zatwierdź")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTransactionDialog = false }) {
                        Text("Anuluj")
                    }
                }
            )
        }

        if (showEditDialog) {
            GoalEditDialog(
                title = "Edytuj cel",
                initialName = goal.name,
                initialTarget = goal.targetAmount.toString(),
                initialSteps = goal.steps,
                isSharedInitially = goal.familyId != null,
                canShare = viewModel.familyId != null,
                onDismiss = { showEditDialog = false },
                onConfirm = { name, target, newSteps, isShared ->
                    viewModel.updateGoal(goal, name, target, newSteps, isShared)
                    showEditDialog = false
                }
            )
        }
    }
}
