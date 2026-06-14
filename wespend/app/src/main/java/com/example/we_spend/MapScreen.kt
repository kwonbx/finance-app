package com.example.we_spend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClusterCountMarker(count: Int, isCluster: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isCluster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        contentColor = if (isCluster) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(2.dp, Color.White),
        modifier = Modifier.size(if (count > 9) 42.dp else 36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, viewModel: MapViewModel) {
    val uiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = true)) }
    val properties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedPlaceExpenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var selectedPlaceName by remember { mutableStateOf("") }

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.destination?.route == "map") {
            viewModel.loadData()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa wydatków", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.expenses.isEmpty()) {
                Text(
                    text = "Brak wydatków z lokalizacją do wyświetlenia.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(52.2297, 21.0122), 6f)
                }

                // Auto-zoom to fit all places when data loads
                LaunchedEffect(viewModel.expenses) {
                    if (viewModel.expenses.isNotEmpty()) {
                        val builder = LatLngBounds.builder()
                        var hasCoords = false
                        viewModel.expenses.forEach { exp ->
                            if (exp.latitude != null && exp.longitude != null) {
                                builder.include(LatLng(exp.latitude, exp.longitude))
                                hasCoords = true
                            }
                        }
                        if (hasCoords) {
                            try {
                                val bounds = builder.build()
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(bounds, 150)
                                )
                            } catch (e: Exception) {
                                // Fallback if bounds are too small
                                viewModel.expenses.firstOrNull()?.let {
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude!!, it.longitude!!), 12f)
                                }
                            }
                        }
                    }
                }

                // Refined clustering logic based on zoom level, using derivedStateOf to avoid unnecessary recompositions
                val clusters by remember(viewModel.expenses) {
                    derivedStateOf {
                        val zoom = cameraPositionState.position.zoom
                        val threshold = when {
                            zoom < 6 -> 3.0
                            zoom < 9 -> 0.8
                            zoom < 11 -> 0.15
                            zoom < 13 -> 0.02
                            zoom < 15 -> 0.002
                            zoom < 17 -> 0.0002
                            else -> 0.00001 // Practically zero, only exact same spot
                        }

                        val result = mutableMapOf<LatLng, MutableList<Expense>>()
                        viewModel.expenses.forEach { exp ->
                            val lat = exp.latitude!!
                            val lng = exp.longitude!!

                            var found = false
                            // Check if this expense belongs to an existing cluster
                            for (clusterLatLng in result.keys) {
                                if (kotlin.math.abs(clusterLatLng.latitude - lat) < threshold &&
                                    kotlin.math.abs(clusterLatLng.longitude - lng) < threshold) {
                                    result[clusterLatLng]?.add(exp)
                                    found = true
                                    break
                                }
                            }
                            if (!found) {
                                result[LatLng(lat, lng)] = mutableListOf(exp)
                            }
                        }
                        result
                    }
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = properties,
                    uiSettings = uiSettings
                ) {
                    clusters.forEach { (latLng, expenses) ->
                        val isCluster = expenses.size > 1
                        val firstExpense = expenses.first()

                        // Use both coordinates and count as a key to force marker update when cluster size changes
                        key(latLng, expenses.size) {
                            MarkerComposable(
                                state = rememberMarkerState(position = latLng),
                                onClick = {
                                    selectedPlaceExpenses = expenses
                                    selectedPlaceName = if (isCluster) "${expenses.size} transakcji w tym obszarze" else firstExpense.shopName.ifBlank { firstExpense.address ?: "Miejsce" }
                                    showBottomSheet = true
                                    true
                                }
                            ) {
                                ClusterCountMarker(count = expenses.size, isCluster = isCluster)
                            }
                        }
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = selectedPlaceName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedPlaceExpenses) { expense ->
                                ExpenseListItem(
                                    expense = expense,
                                    onClick = {
                                        showBottomSheet = false
                                        if (expense.recurringExpenseId != null) {
                                            navController.navigate("edit_recurring/${expense.recurringExpenseId}")
                                        } else {
                                            navController.navigate("edit_expense/${expense.id}")
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
