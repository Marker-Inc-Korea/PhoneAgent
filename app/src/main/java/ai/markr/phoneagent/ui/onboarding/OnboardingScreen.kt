package ai.markr.phoneagent.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ai.markr.phoneagent.platform.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed(context)) }

    LifecycleResumeEffect(Unit) {
        accessibilityEnabled = Permissions.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시작하기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "PhoneAgent는 화면을 읽고 사람 대신 앱을 조작합니다. 아래 권한이 필요해요.",
                style = MaterialTheme.typography.bodyLarge,
            )

            PermissionCard(
                title = "접근성 권한",
                granted = accessibilityEnabled,
                description = "화면의 내용을 읽고 탭/스크롤을 수행하기 위해 필요합니다.",
                buttonText = "접근성 설정 열기",
                onClick = { Permissions.openAccessibilitySettings(context) },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "알림 권한",
                    granted = notificationsGranted,
                    description = "작업 진행 상태를 알림으로 보여주기 위해 필요합니다.",
                    buttonText = "알림 허용",
                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            Button(
                onClick = onDone,
                enabled = accessibilityEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("시작하기") }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$title — ${if (granted) "완료 ✓" else "필요"}",
                style = MaterialTheme.typography.titleMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}

private fun notificationsAllowed(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
