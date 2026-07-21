package com.example.wifirttmeasurement.presentation.ui.publisher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifirttmeasurement.domain.model.ConnectionStatus
import com.example.wifirttmeasurement.domain.model.LogSeverity
import com.example.wifirttmeasurement.domain.model.MeasurementLog
import com.example.wifirttmeasurement.domain.model.PublisherStatus
import com.example.wifirttmeasurement.presentation.theme.RttAmber
import com.example.wifirttmeasurement.presentation.theme.RttGreen
import com.example.wifirttmeasurement.presentation.theme.RttRed
import java.text.DateFormat
import java.util.Date

@Composable
fun PublisherScreen(
    modifier: Modifier = Modifier,
    viewModel: PublisherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorMessageShown()
    }

    PublisherContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onStartPublishing = viewModel::startPublishing,
        onStopPublishing = viewModel::stopPublishing,
        onRefreshStatus = viewModel::refreshStatus,
        modifier = modifier,
    )
}

@Composable
private fun PublisherContent(
    uiState: PublisherUiState,
    snackbarHostState: SnackbarHostState,
    onStartPublishing: () -> Unit,
    onStopPublishing: () -> Unit,
    onRefreshStatus: () -> Unit,
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
            val isExpanded = maxWidth >= 840.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 1100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PublisherHeader()

                    if (isExpanded) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                DeviceInformationCard(uiState)
                                StatusCard(uiState)
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                MeasurementCounterCard(uiState)
                                PublisherControls(
                                    uiState = uiState,
                                    onStartPublishing = onStartPublishing,
                                    onStopPublishing = onStopPublishing,
                                    onRefreshStatus = onRefreshStatus,
                                )
                            }
                        }
                    } else {
                        DeviceInformationCard(uiState)
                        StatusCard(uiState)
                        MeasurementCounterCard(uiState)
                        PublisherControls(
                            uiState = uiState,
                            onStartPublishing = onStartPublishing,
                            onStopPublishing = onStopPublishing,
                            onRefreshStatus = onRefreshStatus,
                        )
                    }

                    PublisherLogCard(logs = uiState.logs)
                }
            }
        }
    }
}

@Composable
private fun PublisherHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Publisher",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Wi-Fi RTT responder status and request telemetry",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceInformationCard(uiState: PublisherUiState) {
    PublisherCard(title = "Device Information") {
        InfoRow(label = "Device Name", value = uiState.deviceName)
        InfoRow(label = "Publisher ID", value = uiState.publisherId)
        InfoRow(
            label = "Battery",
            value = uiState.batteryPercentage?.let { "$it%" } ?: "Unknown",
        )
    }
}

@Composable
private fun StatusCard(uiState: PublisherUiState) {
    PublisherCard(title = "Status") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status Indicator",
                style = MaterialTheme.typography.bodyLarge,
            )
            StatusIndicator(status = uiState.currentStatus)
        }
        InfoRow(label = "Current Status", value = uiState.currentStatus.displayName())
        InfoRow(label = "Connection Status", value = uiState.connectionStatus.displayName())
        InfoRow(
            label = "RTT Requests",
            value = if (uiState.isWaitingForRttRequests) "Waiting" else "Not waiting",
        )
        InfoRow(
            label = "Last Measurement",
            value = uiState.lastMeasurementTimestampMillis.formatTimestamp(),
        )
    }
}

@Composable
private fun MeasurementCounterCard(uiState: PublisherUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Requests Received",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = uiState.requestsReceived.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PublisherControls(
    uiState: PublisherUiState,
    onStartPublishing: () -> Unit,
    onStopPublishing: () -> Unit,
    onRefreshStatus: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onStartPublishing,
                    enabled = !uiState.isBusy && uiState.currentStatus != PublisherStatus.Waiting,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text("Start Publishing")
                }
                OutlinedButton(
                    onClick = onStopPublishing,
                    enabled = !uiState.isBusy && uiState.currentStatus != PublisherStatus.Offline,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text("Stop Publishing")
                }
            }
            OutlinedButton(
                onClick = onRefreshStatus,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh Status")
            }
        }
    }
}

@Composable
private fun PublisherLogCard(logs: List<MeasurementLog>) {
    PublisherCard(title = "Logs") {
        if (logs.isEmpty()) {
            Text(
                text = "No publisher events yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = logs,
                    key = { log -> log.id },
                ) { log ->
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
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(log.severity.color()),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
private fun PublisherCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusIndicator(status: PublisherStatus) {
    val color = status.color()
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = status.displayName(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun PublisherStatus.displayName(): String {
    return when (this) {
        PublisherStatus.Waiting -> "Waiting"
        PublisherStatus.Busy -> "Busy"
        PublisherStatus.Offline -> "Offline"
    }
}

private fun ConnectionStatus.displayName(): String {
    return when (this) {
        ConnectionStatus.Connected -> "Connected"
        ConnectionStatus.Disconnected -> "Disconnected"
        ConnectionStatus.Connecting -> "Connecting"
        ConnectionStatus.Unreachable -> "Unreachable"
    }
}

private fun PublisherStatus.color(): Color {
    return when (this) {
        PublisherStatus.Waiting -> RttGreen
        PublisherStatus.Busy -> RttAmber
        PublisherStatus.Offline -> RttRed
    }
}

private fun LogSeverity.color(): Color {
    return when (this) {
        LogSeverity.Info -> RttGreen
        LogSeverity.Warning -> RttAmber
        LogSeverity.Error -> RttRed
    }
}

private fun Long?.formatTimestamp(): String {
    return this?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Never"
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PublisherContentPreview() {
    PublisherContent(
        uiState = PublisherUiState(
            deviceName = "Pixel Prototype",
            publisherId = "pub-1234abcd",
            currentStatus = PublisherStatus.Waiting,
            connectionStatus = ConnectionStatus.Connected,
            isWaitingForRttRequests = true,
            requestsReceived = 12,
            batteryPercentage = 86,
            lastMeasurementTimestampMillis = System.currentTimeMillis(),
            logs = listOf(
                MeasurementLog(
                    id = "1",
                    timestampMillis = System.currentTimeMillis(),
                    message = "Publisher Started",
                    severity = LogSeverity.Info,
                ),
            ),
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onStartPublishing = {},
        onStopPublishing = {},
        onRefreshStatus = {},
    )
}
