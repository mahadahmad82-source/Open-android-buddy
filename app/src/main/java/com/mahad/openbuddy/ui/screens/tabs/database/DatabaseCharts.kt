package com.mahad.openbuddy.ui.screens.tabs.database

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.mahad.openbuddy.domain.model.QueryResult
import com.mahad.openbuddy.ui.components.EmptyStateView
import com.mahad.openbuddy.ui.components.PillChip
import com.mahad.openbuddy.ui.theme.GlassTheme
import java.text.DecimalFormat
import kotlin.math.min

enum class ChartType(val title: String) {
    BAR("Bar Chart"),
    LINE("Line Chart")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseResultChartView(
    queryResult: QueryResult,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens

    // Identify numeric columns
    val numericColumnIndices = remember(queryResult) {
        val indices = mutableListOf<Int>()
        for (colIdx in queryResult.columns.indices) {
            var validNumbers = 0
            var checked = 0
            for (row in queryResult.rows.take(50)) {
                if (colIdx < row.size) {
                    checked++
                    val cell = row[colIdx].trim()
                    if (cell.toDoubleOrNull() != null) {
                        validNumbers++
                    }
                }
            }
            if (checked > 0 && validNumbers >= checked * 0.7) {
                indices.add(colIdx)
            }
        }
        indices
    }

    if (numericColumnIndices.isEmpty() || queryResult.rows.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.BarChart,
            title = "No Numeric Data Found",
            message = "Charts require at least one numeric column (e.g. integer, float, count, price). Query results only contain text values.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Default value column
    var selectedValueColIdx by remember(queryResult) {
        mutableIntStateOf(numericColumnIndices.first())
    }

    // Default label column (first non-numeric or first column)
    var selectedLabelColIdx by remember(queryResult) {
        val nonNumeric = queryResult.columns.indices.firstOrNull { it !in numericColumnIndices }
        mutableIntStateOf(nonNumeric ?: if (selectedValueColIdx != 0) 0 else min(1, queryResult.columns.size - 1))
    }

    var selectedChartType by remember { mutableStateOf(ChartType.BAR) }
    var valueDropdownExpanded by remember { mutableStateOf(false) }
    var labelDropdownExpanded by remember { mutableStateOf(false) }

    // Parse data points
    val dataPoints = remember(queryResult, selectedValueColIdx, selectedLabelColIdx) {
        val points = mutableListOf<Pair<String, Double>>()
        queryResult.rows.take(40).forEachIndexed { index, row ->
            val label = if (selectedLabelColIdx < row.size && row[selectedLabelColIdx].isNotBlank()) {
                val raw = row[selectedLabelColIdx]
                if (raw.length > 12) raw.take(10) + "…" else raw
            } else {
                "#${index + 1}"
            }

            val rawVal = if (selectedValueColIdx < row.size) row[selectedValueColIdx] else "0"
            val value = rawVal.toDoubleOrNull() ?: 0.0
            points.add(Pair(label, value))
        }
        points
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Controls Row: Chart Type & Columns Selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillChip(
                text = "Bar Chart",
                isSelected = selectedChartType == ChartType.BAR,
                onClick = { selectedChartType = ChartType.BAR },
                testTag = "chart_type_bar"
            )
            PillChip(
                text = "Line Chart",
                isSelected = selectedChartType == ChartType.LINE,
                onClick = { selectedChartType = ChartType.LINE },
                testTag = "chart_type_line"
            )
        }

        // Dropdowns for X and Y axes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Value Column Selector (Y Axis)
            ExposedDropdownMenuBox(
                expanded = valueDropdownExpanded,
                onExpandedChange = { valueDropdownExpanded = !valueDropdownExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = queryResult.columns.getOrElse(selectedValueColIdx) { "Value" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Y-Axis (Value)", fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = valueDropdownExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("chart_value_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = valueDropdownExpanded,
                    onDismissRequest = { valueDropdownExpanded = false }
                ) {
                    numericColumnIndices.forEach { idx ->
                        val colName = queryResult.columns[idx]
                        DropdownMenuItem(
                            text = { Text(colName) },
                            onClick = {
                                selectedValueColIdx = idx
                                valueDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Label Column Selector (X Axis)
            ExposedDropdownMenuBox(
                expanded = labelDropdownExpanded,
                onExpandedChange = { labelDropdownExpanded = !labelDropdownExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = queryResult.columns.getOrElse(selectedLabelColIdx) { "Label" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("X-Axis (Label)", fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labelDropdownExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("chart_label_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = labelDropdownExpanded,
                    onDismissRequest = { labelDropdownExpanded = false }
                ) {
                    queryResult.columns.indices.forEach { idx ->
                        val colName = queryResult.columns[idx]
                        DropdownMenuItem(
                            text = { Text(colName) },
                            onClick = {
                                selectedLabelColIdx = idx
                                labelDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Chart Canvas Box
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.glassFill)
                .border(tokens.glassBorderStroke, shape)
                .padding(12.dp)
        ) {
            when (selectedChartType) {
                ChartType.BAR -> {
                    CustomBarChart(
                        data = dataPoints,
                        primaryColor = tokens.accentViolet,
                        secondaryColor = tokens.accentCyan,
                        textColor = tokens.textSecondary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ChartType.LINE -> {
                    CustomLineChart(
                        data = dataPoints,
                        lineColor = tokens.accentCyan,
                        fillColor = tokens.accentViolet,
                        textColor = tokens.textSecondary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomBarChart(
    data: List<Pair<String, Double>>,
    primaryColor: Color,
    secondaryColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val numberFormatter = remember { DecimalFormat("#,##0.##") }

    val maxVal = remember(data) {
        val m = data.maxOfOrNull { it.second } ?: 1.0
        if (m <= 0.0) 1.0 else m
    }
    val minVal = remember(data) {
        val m = data.minOfOrNull { it.second } ?: 0.0
        if (m > 0.0) 0.0 else m
    }

    val barWidthDp = 36.dp
    val barSpacingDp = 18.dp
    val totalWidthDp = max(320.dp, (barWidthDp + barSpacingDp) * data.size + 60.dp)

    Box(
        modifier = modifier.horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidthDp)
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 32.dp, start = 30.dp, end = 20.dp)
        ) {
            val width = size.width
            val height = size.height
            val range = (maxVal - minVal).let { if (it == 0.0) 1.0 else it }

            // Grid lines (3 horizontal lines)
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = height - (height * (i.toFloat() / gridLines))
                val gridVal = minVal + (range * (i.toDouble() / gridLines))
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )

                // Draw Y-axis label
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(180, 148, 163, 184)
                        textSize = 24f
                        isAntiAlias = true
                    }
                    drawText(numberFormatter.format(gridVal), 4f, y - 6f, paint)
                }
            }

            // Draw Bars
            val barWidthPx = barWidthDp.toPx()
            val barSpacingPx = barSpacingDp.toPx()

            data.forEachIndexed { index, point ->
                val x = 40f + index * (barWidthPx + barSpacingPx)
                val normalizedVal = ((point.second - minVal) / range).toFloat().coerceIn(0.02f, 1f)
                val barHeight = height * normalizedVal
                val y = height - barHeight

                val gradient = Brush.verticalGradient(
                    colors = listOf(secondaryColor, primaryColor),
                    startY = y,
                    endY = height
                )

                // Rounded top bar
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(x, y),
                    size = Size(barWidthPx, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )

                // Value above bar
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 22f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(numberFormatter.format(point.second), x + barWidthPx / 2, y - 8f, paint)
                }

                // Label below bar
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(200, 203, 213, 225)
                        textSize = 22f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(point.first, x + barWidthPx / 2, height + 24f, paint)
                }
            }
        }
    }
}

@Composable
private fun CustomLineChart(
    data: List<Pair<String, Double>>,
    lineColor: Color,
    fillColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val numberFormatter = remember { DecimalFormat("#,##0.##") }

    val maxVal = remember(data) {
        val m = data.maxOfOrNull { it.second } ?: 1.0
        if (m <= 0.0) 1.0 else m
    }
    val minVal = remember(data) {
        val m = data.minOfOrNull { it.second } ?: 0.0
        if (m > 0.0) 0.0 else m
    }

    val pointSpacingDp = 44.dp
    val totalWidthDp = max(320.dp, pointSpacingDp * data.size + 70.dp)

    Box(
        modifier = modifier.horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidthDp)
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 32.dp, start = 30.dp, end = 20.dp)
        ) {
            val width = size.width
            val height = size.height
            val range = (maxVal - minVal).let { if (it == 0.0) 1.0 else it }

            // Grid lines
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = height - (height * (i.toFloat() / gridLines))
                val gridVal = minVal + (range * (i.toDouble() / gridLines))
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }

            if (data.isEmpty()) return@Canvas

            val pointSpacingPx = pointSpacingDp.toPx()
            val points = data.mapIndexed { index, item ->
                val x = 40f + index * pointSpacingPx
                val normalizedVal = ((item.second - minVal) / range).toFloat().coerceIn(0f, 1f)
                val y = height - (height * normalizedVal)
                Offset(x, y)
            }

            // Path for gradient fill
            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Line stroke path
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw Dots, Values and Labels
            points.forEachIndexed { index, pt ->
                // Outer ring
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = pt
                )
                // Inner center dot
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = pt
                )

                // Value label above
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 21f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(numberFormatter.format(data[index].second), pt.x, pt.y - 12f, paint)
                }

                // X-Axis label below
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(200, 203, 213, 225)
                        textSize = 21f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(data[index].first, pt.x, height + 24f, paint)
                }
            }
        }
    }
}
