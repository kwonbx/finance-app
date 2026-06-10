package com.example.we_spend

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(navController: NavController, viewModel: AddExpenseViewModel) {
    val context = LocalContext.current
    var activeDatePicker by remember { mutableStateOf<String?>(null) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )
    var expandedCategory by remember { mutableStateOf(false) }
    val receiptScanner = remember { ReceiptScanner() }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isScanning = true
            receiptScanner.scanReceipt(context, uri,
                onResult = { shop, date, amount ->
                    viewModel.onReceiptScanned(shop, amount, date)
                    isScanning = false
                    Toast.makeText(context, "Zeskanowano paragon!", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    isScanning = false
                    Toast.makeText(context, "Błąd skanowania", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            isScanning = true
            receiptScanner.scanReceipt(context, tempImageUri!!,
                onResult = { shop, date, amount ->
                    viewModel.onReceiptScanned(shop, amount, date)
                    isScanning = false
                    Toast.makeText(context, "Zeskanowano paragon!", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    isScanning = false
                    Toast.makeText(context, "Błąd skanowania", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "receipt_image.jpg")
        tempImageUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraLauncher.launch(tempImageUri!!)
    }

    if (activeDatePicker != null) {
        DatePickerDialog(
            onDismissRequest = { activeDatePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

                        if (activeDatePicker == "EXPENSE") {
                            viewModel.updateExpenseDate(localDate)
                        } else if (activeDatePicker == "NEXT_PAYMENT") {
                            viewModel.updateNextPaymentDate(localDate)
                        }
                    }
                    activeDatePicker = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePicker = null }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dodaj wydatek") },
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
            if (isScanning) {
                CircularProgressIndicator()
                Text("Analizowanie paragonu...", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                        Text("📷 Zrób zdjęcie")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼️ Dodaj z galerii")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Tytuł (np. Zakupy Biedronka)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Kwota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                val expenseDateText = viewModel.expenseDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                OutlinedTextField(
                    value = expenseDateText,
                    onValueChange = {},
                    label = { Text("Data wydatku") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { activeDatePicker = "EXPENSE" }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Wybierz datę")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { activeDatePicker = "EXPENSE" })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = viewModel.type == "Jednorazowy",
                        onClick = { viewModel.updateType("Jednorazowy") }
                    )
                    Text("Jednorazowy", modifier = Modifier.clickable { viewModel.updateType("Jednorazowy") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = viewModel.type == "Stały",
                        onClick = { viewModel.updateType("Stały") }
                    )
                    Text("Stały", modifier = Modifier.clickable { viewModel.updateType("Stały") })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = viewModel.category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    viewModel.categories.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                viewModel.updateCategory(selectionOption)
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            if (viewModel.type == "Stały") {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = viewModel.frequencyDays,
                    onValueChange = { viewModel.updateFrequencyDays(it) },
                    label = { Text("Co ile dni płacisz?") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                val dateText = viewModel.nextPaymentDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

                if (!dateText.isNullOrEmpty()) {
                    Text(
                        text = "Następna płatność: $dateText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                enabled = !viewModel.isLoading,
                onClick = {
                    viewModel.saveExpense(
                        onSuccess = {
                            Toast.makeText(context, "Dodano wydatek!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Zapisz wydatek")
                }
            }
        }
    }
}