package com.runmoni.app.ui

import com.runmoni.app.domain.AutomaticBackupRecordState
import com.runmoni.app.domain.CloudAccountState

internal fun CloudAccountState.automaticBackupMessage(runId: String): String = when (automaticBackup.record(runId)) {
    AutomaticBackupRecordState.SAVED -> "이 계정에 요약을 자동 백업했어요"
    AutomaticBackupRecordState.WAITING -> "자동 백업 대기 중이에요"
    AutomaticBackupRecordState.RETRYING -> "백업을 완료하지 못했어요. 자동 재시도를 기다리고 있어요"
    AutomaticBackupRecordState.CONFLICT -> "서버에 다른 요약이 있어 덮어쓰기를 멈췄어요. 백업한 요약을 확인해 주세요"
    AutomaticBackupRecordState.EXCLUDED -> "이 기록은 현재 계정의 자동 백업 대상에서 제외되어 있어요"
}

internal const val AUTOMATIC_BACKUP_PRIVACY = "완료된 기록의 날짜·거리·시간·GPS 측정 개수만 저장해요. 경로·포인트·옷장은 이 기기에 남아요."
