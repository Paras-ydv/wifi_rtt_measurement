package com.example.wifirttmeasurement.presentation.ui.role

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.model.DeviceCapability
import com.example.wifirttmeasurement.domain.model.RttFailureReason
import com.example.wifirttmeasurement.presentation.theme.RttAmber
import com.example.wifirttmeasurement.presentation.theme.RttGreen

@Composable
fun RoleSelectionScreen(
    onNavigateToRole: (AppRole) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RoleSelectionEvent.NavigateToRole -> onNavigateToRole(event.role)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorMessageShown()
    }

    RoleSelectionContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRoleSelected = viewModel::onRoleSelected,
        onRefreshCapabilities = viewModel::refreshCapabilities,
        modifier = modifier,
    )
}

@Composable
private fun RoleSelectionContent(
    uiState: RoleSelectionUiState,
    snackbarHostState: SnackbarHostState,
    onRoleSelected: (AppRole) -> Unit,
    onRefreshCapabilities: () -> Unit,
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
            val isExpanded = maxWidth >= 720.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 920.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header()

                    CapabilitySummaryCard(
                        uiState = uiState,
                        onRefreshCapabilities = onRefreshCapabilities,
                    )

                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RoleCard(
                                role = AppRole.Receiver,
                                title = "Receiver",
                                subtitle = "Measures distance to every Publisher",
                                enabled = uiState.canChooseReceiver,
                                selected = uiState.selectedRole == AppRole.Receiver,
                                onClick = { onRoleSelected(AppRole.Receiver) },
                                modifier = Modifier.weight(1f),
                            )
                            RoleCard(
                                role = AppRole.Publisher,
                                title = "Publisher",
                                subtitle = "Stays available for RTT requests",
                                enabled = uiState.canChoosePublisher,
                                selected = uiState.selectedRole == AppRole.Publisher,
                                onClick = { onRoleSelected(AppRole.Publisher) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RoleCard(
                                role = AppRole.Receiver,
                                title = "Receiver",
                                subtitle = "Measures distance to every Publisher",
                                enabled = uiState.canChooseReceiver,
                                selected = uiState.selectedRole == AppRole.Receiver,
                                onClick = { onRoleSelected(AppRole.Receiver) },
                            )
                            RoleCard(
                                role = AppRole.Publisher,
                                title = "Publisher",
                                subtitle = "Stays available for RTT requests",
                                enabled = uiState.canChoosePublisher,
                                selected = uiState.selectedRole == AppRole.Publisher,
                                onClick = { onRoleSelected(AppRole.Publisher) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Wi-Fi RTT Lab",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Choose Role",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapabilitySummaryCard(
    uiState: RoleSelectionUiState,
    onRefreshCapabilities: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Device Capability",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AnimatedVisibility(visible = uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            CapabilityRow(
                label = "Wi-Fi RTT",
                available = uiState.capability.isRttAvailable,
            )
            CapabilityRow(
                label = "Receiver mode",
                available = uiState.capability.canActAsReceiver,
            )
            CapabilityRow(
                label = "Publisher mode",
                available = uiState.capability.canActAsPublisher,
            )

            if (!uiState.isLoading && uiState.capability.failureReason != null) {
                Text(
                    text = capabilityMessage(uiState.capability.failureReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = onRefreshCapabilities,
                enabled = !uiState.isLoading,
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    available: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        StatusPill(
            text = if (available) "Available" else "Unavailable",
            color = if (available) RttGreen else RttAmber,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleCard(
    role: AppRole,
    title: String,
    subtitle: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected) {
                    StatusPill(
                        text = "Saved",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("Continue as ${role.name}")
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
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
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun capabilityMessage(failureReason: RttFailureReason): String {
    return when (failureReason) {
        RttFailureReason.RttUnsupported -> "Wi-Fi RTT is unavailable on this device right now."
        RttFailureReason.PermissionDenied -> "Required Wi-Fi RTT permissions are not granted."
        RttFailureReason.None -> "Wi-Fi RTT is available."
        else -> "Device capability check could not be completed."
    }
}

@Preview(showBackground = true)
@Composable
private fun RoleSelectionContentPreview() {
    RoleSelectionContent(
        uiState = RoleSelectionUiState(
            selectedRole = AppRole.Receiver,
            capability = DeviceCapability(
                isRttAvailable = true,
                canActAsReceiver = true,
                canActAsPublisher = true,
            ),
            isLoading = false,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onRoleSelected = {},
        onRefreshCapabilities = {},
    )
}
