package ai.markr.phoneagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.markr.phoneagent.agent.model.AgentStep
import ai.markr.phoneagent.runtime.RunStatus

@Composable
fun ReadinessBanner(
    accessibilityEnabled: Boolean,
    configured: Boolean,
    onFixAccessibility: () -> Unit,
    onFixSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("시작하려면 설정이 필요해요", style = MaterialTheme.typography.titleMedium)
            if (!accessibilityEnabled) {
                Text(
                    "화면을 읽고 조작하려면 접근성 권한이 필요합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onFixAccessibility) { Text("접근성 권한 설정") }
            }
            if (!configured) {
                Text(
                    "LLM API 키와 모델을 설정에서 구성하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onFixSettings) { Text("API 설정 열기") }
            }
        }
    }
}

@Composable
fun StatusChip(status: RunStatus, stepCount: Int) {
    val label = when (status) {
        RunStatus.IDLE -> "대기 중"
        RunStatus.RUNNING -> "실행 중 · ${stepCount}단계"
        RunStatus.DONE -> "완료"
        RunStatus.ABORTED -> "중단됨"
        RunStatus.ERROR -> "오류"
        RunStatus.CANCELLED -> "사용자 중지"
    }
    AssistChip(onClick = {}, label = { Text(label) })
}

@Composable
fun AnswerCard(text: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun StepRow(step: AgentStep) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${step.index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    step.action::class.simpleName.orEmpty() + if (step.usedVision) " (이미지)" else "",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (step.thought.isNotBlank()) {
                Text(step.thought, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                step.actionResult,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
