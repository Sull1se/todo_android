package com.example.data.transfer

data class BackupData(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: String,
    val sourceAppVersion: String,
    val lists: List<TaskListBackupDto>,
    val tasks: List<TaskBackupDto>
) {
    companion object {
        const val BACKUP_FORMAT = "com.aistudio.todo.backup"
        const val CURRENT_FORMAT_VERSION = 2
        const val MAX_FILE_SIZE_BYTES = 32L * 1024L * 1024L // 32 MiB
        const val MAX_LIST_COUNT = 10_000
        const val MAX_TASK_COUNT = 100_000
        const val MAX_NESTING_DEPTH = 16
    }
}

data class TaskListBackupDto(
    val id: Int,
    val name: String,
    val themeColor: Long,
    val displayOrder: Int,
    val completionMode: Int,
    val timestamp: Long,
    val showInSummary: Boolean = true
)

data class TaskBackupDto(
    val id: Int,
    val listId: Int,
    val title: String,
    val content: String,
    val isCompleted: Boolean,
    val isFlagged: Boolean,
    val displayOrder: Int,
    val timestamp: Long
)

fun currentIsoTimestamp(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date())
}
