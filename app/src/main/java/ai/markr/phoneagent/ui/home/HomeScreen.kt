package ai.markr.phoneagent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.markr.phoneagent.runtime.RunStatus
import ai.markr.phoneagent.ui.components.AnswerCard
import ai.markr.phoneagent.ui.components.ReadinessBanner
import ai.markr.phoneagent.ui.components.StatusChip
import ai.markr.phoneagent.ui.components.StepRow

private val EXAMPLES = listOf(
    "Gmail에서 새 메일 확인하고 요약해줘",
    "유튜브에서 'lofi' 검색해줘",
    "크롬 열어서 날씨 검색해줘",
    "설정에서 와이파이 화면 열어줘",
    "지금 화면에 뭐가 있는지 읽어줘",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(TextFieldValue("")) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(runState.steps.size) {
        if (runState.steps.isNotEmpty()) listState.animateScrollToItem(runState.steps.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhoneAgent") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "실행 기록")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!readiness.ready) {
                ReadinessBanner(
                    accessibilityEnabled = readiness.accessibilityEnabled,
                    configured = readiness.configured,
                    onFixAccessibility = onOpenOnboarding,
                    onFixSettings = onOpenSettings,
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { if (it.text.length <= 2000) input = it },
                label = { Text("무엇을 해드릴까요?") },
                placeholder = { Text("예: Gmail에서 새 메일 확인해줘") },
                minLines = 2,
                maxLines = 4,
                enabled = !runState.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { example ->
                    AssistChip(
                        onClick = { input = TextFieldValue(example) },
                        label = { Text(example, style = MaterialTheme.typography.bodyMedium) },
                        enabled = !runState.isRunning,
                    )
                }
            }

            if (runState.isRunning) {
                Button(
                    onClick = viewModel::stop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("중지") }
            } else {
                Button(
                    onClick = { viewModel.run(input.text) },
                    enabled = readiness.ready && input.text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("실행") }
            }

            StatusChip(runState.status, runState.steps.size)

            if (runState.status == RunStatus.DONE && runState.answer.isNotBlank()) {
                AnswerCard(runState.answer)
            } else if (runState.message.isNotBlank() &&
                runState.status in listOf(RunStatus.ERROR, RunStatus.ABORTED, RunStatus.CANCELLED)
            ) {
                AnswerCard(runState.message, isError = true)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(runState.steps) { step -> StepRow(step) }
            }
        }
    }
}
