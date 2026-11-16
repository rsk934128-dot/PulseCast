package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@Stable
data class PermissionManagerScope(
    val hasCamera: Boolean,
    val hasMicrophone: Boolean,
    val allGranted: Boolean,
    val shouldShowRationale: Boolean,
    val permanentlyDenied: Boolean,
    val requestPermissions: () -> Unit,
    val openAppSettings: () -> Unit
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionManager(
    permissions: List<String> = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ),
    featureTitle: String,
    rationaleMessage: String,
    autoRequestOnLaunch: Boolean = false,
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit = {},
    onPermissionResult: (Map<String, Boolean>) -> Unit = {},
    deniedContent: (@Composable (PermissionManagerScope) -> Unit)? = null,
    content: @Composable (PermissionManagerScope) -> Unit
) {
    val context = LocalContext.current
    var hasAttemptedRequest by rememberSaveable(permissions.joinToString(",")) {
        mutableStateOf(false)
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(
        permissions = permissions,
        onPermissionsResult = { results ->
            hasAttemptedRequest = true
            onPermissionResult(results)
        }
    )

    val cameraState = multiplePermissionsState.permissions.firstOrNull {
        it.permission == Manifest.permission.CAMERA
    }
    val micState = multiplePermissionsState.permissions.firstOrNull {
        it.permission == Manifest.permission.RECORD_AUDIO
    }

    val hasCamera = cameraState?.status?.isGranted ?: true
    val hasMicrophone = micState?.status?.isGranted ?: true
    val allGranted = multiplePermissionsState.allPermissionsGranted
    val shouldShowRationale = multiplePermissionsState.shouldShowRationale
    val permanentlyDenied = hasAttemptedRequest && !allGranted && !shouldShowRationale

    val requestAction = {
        multiplePermissionsState.launchMultiplePermissionRequest()
    }

    val openSettingsAction = {
        openPulseCastAppSettings(context)
    }

    val scope = PermissionManagerScope(
        hasCamera = hasCamera,
        hasMicrophone = hasMicrophone,
        allGranted = allGranted,
        shouldShowRationale = shouldShowRationale,
        permanentlyDenied = permanentlyDenied,
        requestPermissions = requestAction,
        openAppSettings = openSettingsAction
    )

    LaunchedEffect(allGranted) {
        if (allGranted) {
            onAllPermissionsGranted()
        }
    }

    LaunchedEffect(autoRequestOnLaunch, permissions) {
        if (autoRequestOnLaunch && !allGranted && !hasAttemptedRequest) {
            multiplePermissionsState.launchMultiplePermissionRequest()
        }
    }

    if (allGranted) {
        content(scope)
    } else if (deniedContent != null) {
        deniedContent(scope)
    } else {
        PermissionRequestCard(
            scope = scope,
            permissions = permissions,
            featureTitle = featureTitle,
            rationaleMessage = rationaleMessage,
            modifier = modifier
        )
    }
}

@Composable
fun PermissionRequestCard(
    scope: PermissionManagerScope,
    permissions: List<String>,
    featureTitle: String,
    rationaleMessage: String,
    modifier: Modifier = Modifier
) {
    val needsCamera = Manifest.permission.CAMERA in permissions
    val needsMic = Manifest.permission.RECORD_AUDIO in permissions
    val accessSubject = when {
        needsCamera && needsMic -> "Camera & Microphone"
        needsCamera -> "Camera"
        needsMic -> "Microphone"
        else -> "Media"
    }

    val containerColor = if (scope.allGranted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_manager_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        scope.allGranted -> Icons.Filled.CheckCircle
                        needsCamera -> Icons.Filled.Videocam
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = if (scope.allGranted) "Permissions granted" else "Permissions required",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (scope.allGranted) "$featureTitle Ready" else "$featureTitle Permissions",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            scope.allGranted -> "$accessSubject access granted."
                            scope.permanentlyDenied -> "Permission access was denied. Enable $accessSubject in app settings."
                            else -> rationaleMessage
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!scope.allGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (needsCamera) {
                        AccompanistPermissionChip(
                            label = if (scope.hasCamera) "Camera OK" else "Camera",
                            granted = scope.hasCamera,
                            icon = Icons.Filled.Videocam
                        )
                    }
                    if (needsMic) {
                        AccompanistPermissionChip(
                            label = if (scope.hasMicrophone) "Mic OK" else "Mic",
                            granted = scope.hasMicrophone,
                            icon = Icons.Filled.Mic
                        )
                    }
                }

                if (scope.permanentlyDenied) {
                    FilledTonalButton(
                        onClick = scope.openAppSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_open_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open App Settings")
                    }
                } else {
                    Button(
                        onClick = scope.requestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_grant_button")
                    ) {
                        Text(
                            if (scope.shouldShowRationale) "Continue & Grant Access"
                            else "Allow $accessSubject"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccompanistPermissionChip(
    label: String,
    granted: Boolean,
    icon: ImageVector
) {
    Surface(
        color = if (granted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun openPulseCastAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
