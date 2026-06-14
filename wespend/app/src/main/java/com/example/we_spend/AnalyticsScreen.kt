package com.example.we_spend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavController, viewModel: AnalyticsViewModel) {
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analityka", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
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
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Podsumowanie kategorii (Wydatki)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.expenses.isEmpty()) {
                    Text("Brak danych o wydatkach w tym miesiącu.")
                } else {
                    PieChart(expenses = viewModel.expenses)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Przychody vs Wydatki (W czasie)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.expenses.isEmpty() && viewModel.revenues.isEmpty()) {
                    Text("Brak danych do porównania.")
                } else {
                    ComparisonLineChart(expenses = viewModel.expenses, revenues = viewModel.revenues)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PieChart(expenses: List<Expense>) {
    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    val totalAmount = categoryTotals.values.sum()
    val sortedCategories = categoryTotals.entries.sortedByDescending { it.value }

    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    val lightColors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
        Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFF4DB6AC),
        Color(0xFF90A4AE), Color(0xFFA1887F), Color(0xFFFF8A65)
    )

    val darkColors = listOf(
        Color(0xFFEF9A9A), Color(0xFFA5D6A7), Color(0xFF90CAF9),
        Color(0xFFFFF59D), Color(0xFFCE93D8), Color(0xFF80CBC4),
        Color(0xFFB0BEC5), Color(0xFFBCAAA4), Color(0xFFFFCCBC)
    )

    val colors = if (isSystemInDarkTheme()) darkColors else lightColors

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                var startAngle = 0f
                sortedCategories.forEachIndexed { index, entry ->
                    val percentage = (entry.value / totalAmount).toFloat()
                    val sweepAngle = percentage * 360f

                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )

                    if (percentage > 0.03) {
                        val middleAngle = startAngle + (sweepAngle / 2)
                        val angleInRadians = Math.toRadians(middleAngle.toDouble())
                        val radius = size.width / 2.8f
                        val x = (size.width / 2) + cos(angleInRadians).toFloat() * radius
                        val y = (size.height / 2) + sin(angleInRadians).toFloat() * radius

                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color =  onPrimaryColor
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = 14.sp.toPx()
                                isFakeBoldText = true
                                setShadowLayer(2f, 0f, 0f, primaryColor)
                            }
                            drawText(
                                String.format(Locale.getDefault(), "%.0f%%", percentage * 100),
                                x,
                                y + (paint.textSize / 3),
                                paint
                            )
                        }
                    }

                    startAngle += sweepAngle
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column {
            sortedCategories.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(colors[index % colors.size])
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.key}: ${String.format(Locale.getDefault(), "%.2f", entry.value)} zł (${String.format(Locale.getDefault(), "%.1f", (entry.value / totalAmount) * 100)}%)",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonLineChart(expenses: List<Expense>, revenues: List<Revenue>) {
    val calendar = Calendar.getInstance()
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

    val dailyExpenses = DoubleArray(daysInMonth + 1)
    val dailyRevenues = DoubleArray(daysInMonth + 1)

    val sdf = SimpleDateFormat("d", Locale.getDefault())

    expenses.forEach {
        val day = sdf.format(Date(it.dateInMillis)).toInt()
        if (day <= daysInMonth) {
            dailyExpenses[day] += it.amount
        }
    }

    revenues.forEach {
        val day = sdf.format(Date(it.dateInMillis)).toInt()
        if (day <= daysInMonth) {
            dailyRevenues[day] += it.amount
        }
    }

    val cumulativeExpenses = DoubleArray(currentDay + 1)
    val cumulativeRevenues = DoubleArray(currentDay + 1)
    var currentSumExp = 0.0
    var currentSumRev = 0.0

    for (i in 1..currentDay) {
        currentSumExp += dailyExpenses[i]
        currentSumRev += dailyRevenues[i]
        cumulativeExpenses[i] = currentSumExp
        cumulativeRevenues[i] = currentSumRev
    }

    val maxAmount = currentSumExp.coerceAtLeast(currentSumRev).coerceAtLeast(100.0).toFloat()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val gridColor = Color.LightGray.copy(alpha = 0.5f)

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val stepX = size.width / (currentDay - 1).coerceAtLeast(1)
                            val day = (offset.x / stepX).roundToInt() + 1
                            selectedDay = day.coerceIn(1, currentDay)
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (currentDay - 1).coerceAtLeast(1)

                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = height - (i.toFloat() / gridLines * height)
                    drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 0.5.dp.toPx())
                }

                val revenuePath = Path()
                for (i in 1..currentDay) {
                    val x = (i - 1) * stepX
                    val y = height - (cumulativeRevenues[i].toFloat() / maxAmount * height)
                    if (i == 1) revenuePath.moveTo(x, y) else revenuePath.lineTo(x, y)
                    drawCircle(tertiaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
                }
                drawPath(revenuePath, tertiaryColor, style = Stroke(width = 2.dp.toPx()))

                val expensePath = Path()
                for (i in 1..currentDay) {
                    val x = (i - 1) * stepX
                    val y = height - (cumulativeExpenses[i].toFloat() / maxAmount * height)
                    if (i == 1) expensePath.moveTo(x, y) else expensePath.lineTo(x, y)
                    drawCircle(errorColor, radius = 3.dp.toPx(), center = Offset(x, y))
                }
                drawPath(expensePath, errorColor, style = Stroke(width = 2.dp.toPx()))

                selectedDay?.let { day ->
                    val x = (day - 1) * stepX
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        selectedDay?.let { day ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dzień $day",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Przychody: ${String.format(Locale.getDefault(), "%.2f zł", cumulativeRevenues[day])}",
                        color = tertiaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Wydatki: ${String.format(Locale.getDefault(), "%.2f zł", cumulativeExpenses[day])}",
                        color = errorColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
