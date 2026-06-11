package ai.markr.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.markr.phoneagent.data.AgentSettings
import ai.markr.phoneagent.data.LlmProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val s = state.settings
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LLM 제공자", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val providers = LlmProvider.entries
                providers.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = s.provider == p,
                        onClick = { viewModel.onProviderChange(p) },
                        shape = SegmentedButtonDefaults.itemShape(index, providers.size),
                    ) { Text(providerLabel(p)) }
                }
            }

            OutlinedTextField(
                value = s.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("API Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = s.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "키 숨기기" else "키 보기",
                        )
                    }
                },
                supportingText = { Text("키는 기기 보안 저장소(Keystore)로 암호화되어 저장됩니다.") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = s.textModel,
                onValueChange = viewModel::onTextModelChange,
                label = { Text("텍스트 모델") },
                placeholder = { Text(AgentSettings.defaultTextModel(s.provider)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = s.visionModel,
                onValueChange = viewModel::onVisionModelChange,
                label = { Text("비전 모델 (선택 — 비우면 화면 이미지 분석 비활성)") },
                placeholder = { Text(AgentSettings.defaultVisionModel(s.provider)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("최대 단계 수: ${s.maxSteps}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.maxSteps.toFloat(),
                onValueChange = { viewModel.onMaxStepsChange(it.toInt()) },
                valueRange = 5f..40f,
                steps = 34,
            )

            OutlinedButton(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (state.testStatus) {
                        TestStatus.TESTING -> "연결 확인 중…"
                        else -> "연결 테스트"
                    },
                )
            }
            if (state.testMessage.isNotBlank()) {
                Text(
                    state.testMessage,
                    color = when (state.testStatus) {
                        TestStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saved) "저장됨 ✓" else "저장")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun providerLabel(p: LlmProvider) = when (p) {
    LlmProvider.ANTHROPIC -> "Anthropic"
    LlmProvider.OPENAI -> "OpenAI호환"
    LlmProvider.GEMINI -> "Gemini"
}
