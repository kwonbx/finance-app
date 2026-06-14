package com.example.we_spend

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    navController: NavController, 
    viewModel: LocationViewModel,
    title: String = "Wybierz lokalizację"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialPos = LatLng(viewModel.latitude ?: 52.2297, viewModel.longitude ?: 21.0122)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 15f)
    }

    var pickedLocation by remember { mutableStateOf<LatLng?>(if (viewModel.latitude != null) LatLng(viewModel.latitude!!, viewModel.longitude!!) else null) }
    var pickedAddressName by remember { mutableStateOf<String?>(null) }
    val markerState = rememberMarkerState()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    fun getCurrentLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                pickedLocation = currentLatLng
                searchQuery = ""
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    val resolvedAddress = withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            addresses?.firstOrNull()?.getAddressLine(0)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    pickedAddressName = resolvedAddress
                    if (resolvedAddress != null) searchQuery = resolvedAddress
                }
            }
        }
    }

    // Search logic with improved robustness and error handling
    LaunchedEffect(searchQuery) {
        // Skip searching if the query is the same as the already picked address
        if (searchQuery == pickedAddressName && searchQuery.isNotEmpty()) {
            return@LaunchedEffect
        }

        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.length < 3) {
            searchResults = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        delay(600) // Slightly longer debounce to avoid hitting rate limits
        isSearching = true
        searchError = null
        
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    withContext(Dispatchers.Main) {
                        searchError = "Usługa geolokalizacji jest niedostępna"
                    }
                    return@withContext
                }

                val geocoder = Geocoder(context, Locale.getDefault())
                val targetLat = cameraPositionState.position.target.latitude
                val targetLng = cameraPositionState.position.target.longitude

                // 1. Try biased search first to get results near the current map view
                // We use a larger pool of results (up to 15) to allow better local prioritization
                val localAddresses = try {
                    geocoder.getFromLocationName(
                        trimmedQuery,
                        15,
                        targetLat - 0.8,
                        targetLng - 0.8,
                        targetLat + 0.8,
                        targetLng + 0.8
                    )
                } catch (e: Exception) {
                    null
                }

                // 2. Try global search as a complement
                val globalAddresses = try {
                    geocoder.getFromLocationName(trimmedQuery, 10)
                } catch (e: Exception) {
                    null
                }
                
                // Combine results, keeping local ones first
                val combined = (localAddresses ?: emptyList()) + (globalAddresses ?: emptyList())
                val distinctResults = combined.distinctBy { "${it.latitude},${it.longitude}" }

                // If there are many search results, prioritize them by distance to the currently zoomed-in location
                // This ensures that when multiple places match the name, the most relevant nearby one is shown first.
                val finalResults = if (distinctResults.size > 4) {
                    distinctResults.sortedBy { address ->
                        val distanceResults = FloatArray(1)
                        android.location.Location.distanceBetween(
                            targetLat, targetLng,
                            address.latitude, address.longitude,
                            distanceResults
                        )
                        distanceResults[0]
                    }
                } else {
                    distinctResults
                }

                withContext(Dispatchers.Main) {
                    searchResults = finalResults
                    if (finalResults.isEmpty()) {
                        searchError = "Nie znaleziono wyników"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    searchError = "Błąd wyszukiwania: ${e.localizedMessage ?: "Spróbuj ponownie"}"
                    searchResults = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSearching = false
                }
            }
        }
    }

    // Synchronize marker position with pickedLocation
    LaunchedEffect(pickedLocation) {
        pickedLocation?.let { markerState.position = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
        },
        bottomBar = {
            if (pickedLocation != null) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        pickedAddressName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                                maxLines = 2
                            )
                        }
                        
                        Button(
                            onClick = {
                                pickedLocation?.let { latLng ->
                                    scope.launch {
                                        val addressText = pickedAddressName ?: withContext(Dispatchers.IO) {
                                            try {
                                                val geocoder = Geocoder(context, Locale.getDefault())
                                                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                                addresses?.firstOrNull()?.getAddressLine(0)
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        viewModel.updateLocation(latLng.latitude, latLng.longitude, addressText)
                                        navController.popBackStack()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Zatwierdź tę lokalizację")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { getCurrentLocation() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Moja lokalizacja")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false),
                onMapClick = {
                    pickedLocation = it
                    pickedAddressName = null
                    searchQuery = ""
                    searchResults = emptyList()
                    
                    // Resolve address immediately on click
                    scope.launch {
                        val resolvedAddress = withContext(Dispatchers.IO) {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                                addresses?.firstOrNull()?.getAddressLine(0)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        pickedAddressName = resolvedAddress
                        if (resolvedAddress != null) searchQuery = resolvedAddress
                    }
                }
            ) {
                if (pickedLocation != null) {
                    Marker(
                        state = markerState,
                        title = "Wybrane miejsce"
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        placeholder = { Text("Szukaj miejsca lub adresu...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Wyczyść")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { 
                            // Result selection or first result could be handled here if needed
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }

                AnimatedVisibility(
                    visible = searchResults.isNotEmpty() || searchError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        if (searchError != null) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(searchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            LazyColumn {
                                items(searchResults) { address ->
                                    // Improved logic to show place name instead of street number
                                    val isFeatureNumeric = address.featureName?.all { it.isDigit() || it == '/' || it == '-' } == true
                                    val mainText = if (isFeatureNumeric || address.featureName == address.subThoroughfare) {
                                        val street = address.thoroughfare ?: ""
                                        val house = address.subThoroughfare ?: ""
                                        if (street.isNotEmpty()) "$street $house".trim() else address.getAddressLine(0)
                                    } else {
                                        address.featureName ?: address.getAddressLine(0)
                                    }
                                    val subText = if (mainText != address.getAddressLine(0)) address.getAddressLine(0) else ""

                                    ListItem(
                                        headlineContent = { Text(mainText) },
                                        supportingContent = { if (subText.isNotEmpty()) Text(subText) },
                                        modifier = Modifier.clickable {
                                            val latLng = LatLng(address.latitude, address.longitude)
                                            pickedLocation = latLng
                                            val addressLine = address.getAddressLine(0)
                                            pickedAddressName = addressLine
                                            searchQuery = addressLine
                                            searchResults = emptyList()
                                            scope.launch {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                                            }
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (pickedLocation == null && searchQuery.isEmpty()) {
                Text(
                    text = "Kliknij na mapie lub wyszukaj miejsce",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
