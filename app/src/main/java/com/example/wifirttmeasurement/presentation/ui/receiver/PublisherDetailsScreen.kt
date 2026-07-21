package com.example.wifirttmeasurement.presentation.ui.receiver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.presentation.theme.RttAmber
import com.example.wifirttmeasurement.presentation.theme.RttBlue
import com.example.wifirttmeasurement.presentation.theme.RttGreen
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherDetailsScreen(
    publisherId: String,
    onNavigateBack: () -> Unit,
    viewModel: PublisherDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.publisher?.name ?: publisherId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.publisher?.let { publisher ->
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Publisher Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        LabelValue("ID", publisher.id)
                        LabelValue("Status", publisher.status.name)
                        LabelValue("Connection", publisher.connectionStatus.name)
                        LabelValue("Last RSSI", publisher.lastRssiDbm?.let { "$it dBm" } ?: "-")
                        publisher.lastMeasurementTimestampMillis?.let {
                            LabelValue("Last Seen", DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it)))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailsStatCard("Avg", uiState.averageDistanceMeters.fmtDist(), Modifier.weight(1f))
                DetailsStatCard("Min", uiState.minDistanceMeters.fmtDist(), Modifier.weight(1f))
                DetailsStatCard("Max", uiState.maxDistanceMeters.fmtDist(), Modifier.weight(1f))
                DetailsStatCard(
                    "Success",
                    uiState.successRate?.let { "${(it * 100).roundToInt()}%" } ?: "-",
                    Modifier.weight(1f),
                )
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Distance History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DetailsDistanceGraph(measurements = uiState.measurements)
                }
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Measurements (${uiState.measurements.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.measurements.isEmpty()) {
                        Text(
                            "No measurements for this publisher yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.measurements.take(100), key = { it.measurementNumber }) { m ->
                                DetailsMeasurementRow(m)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsDistanceGraph(measurements: List<MeasurementResult>) {
    val points = measurements.asReversed().filter { it.distanceMeters != null }.takeLast(60)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (points.size < 2) {
            Text(
                "Graph updates when measurements arrive",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val distances = points.mapNotNull { it.distanceMeters }
                val min = distances.minOrNull() ?: 0.0
                val max = distances.maxOrNull() ?: 1.0
                val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                val stepX = size.width / points.lastIndex.coerceAtLeast(1)
                val path = Path()
                points.forEachIndexed { i, m ->
                    val d = m.distanceMeters ?: return@forEachIndexed
                    val x = i * stepX
                    val y = size.height - (((d - min) / range).toFloat() * size.height)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, RttBlue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, m ->
                    val d = m.distanceMeters ?: return@forEachIndexed
                    val x = i * stepX
                    val y = size.height - (((d - min) / range).toFloat() * size.height)
                    drawCircle(
                        color = if (m.status == MeasurementStatus.Success) RttGreen else RttAmber,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsMeasurementRow(m: MeasurementResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (m.status == MeasurementStatus.Success) RttGreen else RttAmber)
                .align(Alignment.CenterVertically),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "#${m.measurementNumber} · Round ${m.roundNumber} · ${m.distanceMeters.fmtDist()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "RSSI ${m.rssiDbm ?: "-"} · Std Dev ${m.distanceStandardDeviationMeters.fmtDist()} · ${m.status.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(m.timestampMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}

private fun Double?.fmtDist(): String = this?.let { "${((it * 100).roundToInt() / 100.0)} m" } ?: "-"
