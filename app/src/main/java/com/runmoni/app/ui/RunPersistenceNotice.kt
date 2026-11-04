package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPersistenceState

/** Local durability and wallet processing are separate from Google summary backup. */
@Composable
internal fun RunPersistenceNotice(state: RunPersistenceState, onRetry: () -> Unit) {
    if (state.initialized && state.issue == null && !state.finishing && !state.creditPending && !state.historyUnavailable) return
    val title = when {
        state.issue == RunPersistenceIssue.FINISH -> "마치기 저장을 다시 확인해 주세요"
        state.issue == RunPersistenceIssue.CHECKPOINT -> "기기 저장을 다시 시도해 주세요"
        state.issue == RunPersistenceIssue.RECOVERY -> "기기 기록 확인 필요"
        state.finishing -> "러닝 기록 저장 중"
        state.issue == null && state.historyUnavailable -> "기록 목록을 다시 확인해 주세요"
        state.issue == RunPersistenceIssue.CREDIT || state.creditPending ->
            if (state.pendingCreditRunIds.isEmpty()) "이전 포인트 적립 확인 필요" else "기록은 저장됐어요 · 포인트 처리 필요"
        else -> "저장한 기록 확인 중"
    }
    Surface(color = RunmoniColors.Mint, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("run_persistence_notice")
            .semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            val description = state.message ?: if (state.finishing)
                "기기에 기록을 저장하고 있어요. 완료될 때까지 이 화면에서 기다려 주세요."
            else "중단된 러닝이 있는지 확인한 뒤 시작할 수 있어요."
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (state.canRetry) OutlinedButton(onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("retry_run_save")) {
                Text(if (state.issue == RunPersistenceIssue.CREDIT) "포인트 처리 다시 시도" else "저장 다시 시도")
            } else if (state.saving || !state.initialized || state.finishing)
                LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
