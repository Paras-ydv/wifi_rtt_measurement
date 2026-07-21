package com.example.wifirttmeasurement.presentation.ui.receiver

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.DashboardStats
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.MeasurementResult
import com.example.wifirttmeasurement.domain.model.MeasurementStatus
import com.example.wifirttmeasurement.domain.model.PublisherDevice
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.presentation.components.PermissionDeniedDialog
import com.example.wifirttmeasurement.presentation.components.RttPermissionRequest
import com.example.wifirttmeasurement.presentation.theme.RttAmber
import com.example.wifirttmeasurement.presentation.theme.RttBlue
import com.example.wifirttmeasurement.presentation.theme.RttGreen
import com.example.wifirttmeasurement.presentation.theme.RttRed
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ReceiverScreen(
    onPublisherSelected: (publisherId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.exportedCsvUri) {
        val uri = uiState.exportedCsvUri ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share RTT Measurements"))
        viewModel.onExportUriConsumed()
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorMessageShown()
    }

    if (uiState.showPermissionRequest) {
        RttPermissionRequest(
            onPermissionsResult = viewModel::onPermissionsResult,
        )
    }

    if (uiState.showPermissionDeniedDialog) {
        PermissionDeniedDialog(
            onDismiss = viewModel::dismissPermissionRequest,
            onRetry = { viewModel.scanPublishers() },
        )
    }

    ReceiverContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onScan = viewModel::scanPublishers,
        onMeasureSelected = viewModel::measureSelected,
        onMeasureAll = viewModel::measureAll,
        onStop = viewModel::stop,
        onExportCsv = viewModel::exportCsv,
        onPublisherSelected = onPublisherSelected,
        onPublisherChecked = viewModel::togglePublisherSelection,
        modifier = modifier,
    )
}

@Composable
private fun ReceiverContent(
    uiState: ReceiverUiState,
    snackbarHostState: SnackbarHostState,
    onScan: () -> Unit,
    onMeasureSelected: () -> Unit,
    onMeasureAll: () -> Unit,
    onStop: () -> Unit,
    onExportCsv: () -> Unit,
    onPublisherSelected: (String) -> Unit,
    onPublisherChecked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isExpanded = maxWidth >= 920.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 1200.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ReceiverHeader()
                    MeasurementControls(
                        uiState = uiState,
                        onScan = onScan,
                        onMeasureSelected = onMeasureSelected,
                        onMeasureAll = onMeasureAll,
                        onStop = onStop,
                        onExportCsv = onExportCsv,
                    )
                    StatisticsCards(stats = uiState.dashboardStats)

                    if (isExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                PublisherListCard(
                                    publishers = uiState.publishers,
                                    onPublisherSelected = onPublisherSelected,
                                    onPublisherChecked = onPublisherChecked,
                                )
                                MeasurementLogCard(logs = uiState.logs)
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                LiveGraphCard(measurements = uiState.measurements)
                                MeasurementTableCard(measurements = uiState.measurements)
                            }
                        }
                    } else {
                        PublisherListCard(
                            publishers = uiState.publishers,
                            onPublisherSelected = onPublisherSelected,
                            onPublisherChecked = onPublisherChecked,
                        )
                        LiveGraphCard(measurements = uiState.measurements)
                        MeasurementTableCard(measurements = uiState.measurements)
                        MeasurementLogCard(logs = uiState.logs)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Receiver",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Scan publishers, run sequential Wi-Fi RTT rounds, and inspect live measurements",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeasurementControls(
    uiState: ReceiverUiState,
    onScan: () -> Unit,
    onMeasureSelected: () -> Unit,
    onMeasureAll: () -> Unit,
    onStop: () -> Unit,
    onExportCsv: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Measurement Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onScan,
                    enabled = !uiState.isScanning && !uiState.isMeasuring,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text(if (uiState.isScanning) "Scanning" else "Scan")
                }
                OutlinedButton(
                    onClick = onMeasureSelected,
                    enabled = uiState.hasSelectedPublishers && !uiState.isScanning && !uiState.isMeasuring,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text("Measure Selected")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onMeasureAll,
                    enabled = uiState.hasPublishers && !uiState.isScanning && !uiState.isMeasuring,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text("Measure All")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = uiState.isScanning || uiState.isMeasuring,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text("Stop")
                }
            }
            OutlinedButton(
                onClick = onExportCsv,
                enabled = uiState.measurements.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export CSV")
            }
        }
    }
}

@Composable
private fun StatisticsCards(stats: DashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Avg", stats.averageDistanceMeters.formatDistance(), Modifier.weight(1f))
            StatCard("Min", stats.minimumDistanceMeters.formatDistance(), Modifier.weight(1f))
            StatCard("Max", stats.maximumDistanceMeters.formatDistance(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Median", stats.medianDistanceMeters.formatDistance(), Modifier.weight(1f))
            StatCard("Std Dev", stats.standardDeviationMeters.formatDistance(), Modifier.weight(1f))
            StatCard("Rate", "${stats.measurementRateHz.formatNumber()} Hz", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Total", stats.totalMeasurements.toString(), Modifier.weight(1f))
            StatCard("Active", stats.activePublishers.toString(), Modifier.weight(1f))
            StatCard("Current", stats.currentMeasurements.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PublisherListCard(
    publishers: List<PublisherDevice>,
    onPublisherSelected: (String) -> Unit,
    onPublisherChecked: (String) -> Unit,
) {
    ReceiverCard(title = "Available Publishers") {
        if (publishers.isEmpty()) {
            EmptyText("No publishers discovered yet")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(publishers, key = { it.id }) { publisher ->
                    PublisherRow(
                        publisher = publisher,
                        onPublisherSelected = onPublisherSelected,
                        onPublisherChecked = onPublisherChecked,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublisherRow(
    publisher: PublisherDevice,
    onPublisherSelected: (String) -> Unit,
    onPublisherChecked: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPublisherSelected(publisher.id) }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = publisher.isSelected,
            onCheckedChange = { onPublisherChecked(publisher.id) },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = publisher.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = publisher.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${publisher.connectionStatus.displayName()} · ${publisher.lastMeasuredDistanceMeters.formatDistance()} · RSSI ${publisher.lastRssiDbm?.toString() ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusDot(color = publisher.connectionStatus.color())
    }
}

@Composable
private fun LiveGraphCard(measurements: List<MeasurementResult>) {
    ReceiverCard(title = "Live Graph") {
        DistanceGraph(measurements = measurements)
    }
}

@Composable
private fun DistanceGraph(measurements: List<MeasurementResult>) {
    val successfulMeasurements = measurements
        .asReversed()
        .filter { it.distanceMeters != null }
        .takeLast(120)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (successfulMeasurements.size < 2) {
            EmptyText("Graph updates when distance measurements arrive")
        } else {
            val lineColor = RttBlue
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val distances = successfulMeasurements.mapNotNull { it.distanceMeters }
                val min = distances.minOrNull() ?: 0.0
                val max = distances.maxOrNull() ?: 1.0
                val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                val stepX = size.width / (successfulMeasurements.lastIndex.coerceAtLeast(1))
                val path = Path()

                successfulMeasurements.forEachIndexed { index, measurement ->
                    val distance = measurement.distanceMeters ?: return@forEachIndexed
                    val x = index * stepX
                    val y = size.height - (((distance - min) / range).toFloat() * size.height)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
                successfulMeasurements.forEachIndexed { index, measurement ->
                    val distance = measurement.distanceMeters ?: return@forEachIndexed
                    val x = index * stepX
                    val y = size.height - (((distance - min) / range).toFloat() * size.height)
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementTableCard(measurements: List<MeasurementResult>) {
    ReceiverCard(title = "Current Measurements") {
        if (measurements.isEmpty()) {
            EmptyText("No measurements yet")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(measurements.take(50), key = { "${it.publisherId}-${it.measurementNumber}" }) { measurement ->
                    MeasurementRow(measurement)
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(measurement: MeasurementResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(color = measurement.status.color())
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = measurement.publisherName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Distance ${measurement.distanceMeters.formatDistance()} · Std Dev ${measurement.distanceStandardDeviationMeters.formatDistance()} · RSSI ${measurement.rssiDbm ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Round ${measurement.roundNumber} · Count ${measurement.measurementNumber} · ${measurement.timestampMillis.formatTimestamp()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeasurementLogCard(logs: List<MeasurementLog>) {
    ReceiverCard(title = "Measurement Logs") {
        if (logs.isEmpty()) {
            EmptyText("No receiver events yet")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs.take(80), key = { it.id }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: MeasurementLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(color = log.severity.color())
        Column {
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = log.timestampMillis.formatTimestamp(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReceiverCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun Double?.formatDistance(): String {
    return this?.let { "${it.formatNumber()} m" } ?: "-"
}

private fun Double.formatNumber(): String {
    return ((this * 100.0).roundToInt() / 100.0).toString()
}

private fun Long.formatTimestamp(): String {
    return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(this))
}

private fun ConnectionStatus.displayName(): String {
    return when (this) {
        ConnectionStatus.Connected -> "Connected"
        ConnectionStatus.Disconnected -> "Disconnected"
        ConnectionStatus.Connecting -> "Connecting"
        ConnectionStatus.Unreachable -> "Unreachable"
    }
}

private fun ConnectionStatus.color(): Color {
    return when (this) {
        ConnectionStatus.Connected -> RttGreen
        ConnectionStatus.Connecting -> RttAmber
        ConnectionStatus.Disconnected,
        ConnectionStatus.Unreachable -> RttRed
    }
}

private fun MeasurementStatus.color(): Color {
    return when (this) {
        MeasurementStatus.Success -> RttGreen
        MeasurementStatus.Failed,
        MeasurementStatus.Timeout,
        MeasurementStatus.Unsupported,
        MeasurementStatus.PermissionDenied -> RttAmber
    }
}

private fun LogSeverity.color(): Color {
    return when (this) {
        LogSeverity.Info -> RttGreen
        LogSeverity.Warning -> RttAmber
        LogSeverity.Error -> RttRed
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun ReceiverContentPreview() {
    val now = System.currentTimeMillis()
    ReceiverContent(
        uiState = ReceiverUiState(
            publishers = listOf(
                PublisherDevice(
                    id = "pub-1234abcd",
                    name = "Publisher A",
                    connectionStatus = ConnectionStatus.Connected,
                    status = PublisherStatus.Waiting,
                    lastMeasuredDistanceMeters = 2.31,
                    lastRssiDbm = -54,
                    lastMeasurementTimestampMillis = now,
                    isSelected = true,
                ),
            ),
            measurements = listOf(
                MeasurementResult(
                    timestampMillis = now,
                    publisherId = "pub-1234abcd",
                    publisherName = "Publisher A",
                    distanceMeters = 2.31,
                    distanceStandardDeviationMeters = 0.15,
                    rssiDbm = -54,
                    status = MeasurementStatus.Success,
                    failureReason = RttFailureReason.None,
                    roundNumber = 1,
                    measurementNumber = 1,
                ),
            ),
            dashboardStats = DashboardStats(
                currentMeasurements = 1,
                averageDistanceMeters = 2.31,
                minimumDistanceMeters = 2.31,
                maximumDistanceMeters = 2.31,
                medianDistanceMeters = 2.31,
                standardDeviationMeters = 0.0,
                totalMeasurements = 1,
                measurementRateHz = 0.0,
                activePublishers = 1,
            ),
            logs = listOf(
                MeasurementLog("1", now, "Measurement Completed", LogSeverity.Info),
            ),
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onScan = {},
        onMeasureSelected = {},
        onMeasureAll = {},
        onStop = {},
        onExportCsv = {},
        onPublisherSelected = {},
        onPublisherChecked = {},
    )
}
