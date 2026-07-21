package com.example.wifirttmeasurement.presentation.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun RttPermissionRequest(
    onPermissionsResult: (fineLocation: Boolean, nearbyWifi: Boolean) -> Unit,
) {
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val fineLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val nearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        } else {
            fineLocation
        }
        onPermissionsResult(fineLocation, nearbyWifi)
    }

    LaunchedEffect(Unit) { launcher.launch(permissions) }
}

@Composable
fun PermissionDeniedDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Text(
                "Wi-Fi RTT ranging requires Fine Location" +
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) " and Nearby Wi-Fi Devices" else "") +
                    " permissions. Please grant them to continue.",
            )
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("Retry") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}
