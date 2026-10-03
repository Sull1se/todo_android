package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class UndoActionType(val actionName: String) {
    FLAG("已设为置顶"),
    UNFLAG("已取消置顶"),
    COMPLETE("任务已完成")
}

data class UndoRecord(
    val eventId: Long,
    val taskId: Int,
    val taskTitle: String,
    val listId: Int,
    val originalMIndex: Int,
    val originalFlagged: Boolean,
    val originalCompleted: Boolean,
    val actionType: UndoActionType,
    val createdAt: Long = System.currentTimeMillis()
)

class UndoController {
    private val idGenerator = AtomicLong(1)
    private val _undoRecords = MutableStateFlow<List<UndoRecord>>(emptyList())
    val undoRecords: StateFlow<List<UndoRecord>> = _undoRecords.asStateFlow()

    fun nextEventId(): Long = idGenerator.getAndIncrement()

    /**
     * 压入新的撤销记录。新提示位于最上层（列表首部）。
     */
    fun pushRecord(record: UndoRecord) {
        // 同一任务的旧撤销失效并被新记录替代
        val filtered = _undoRecords.value.filter { it.taskId != record.taskId }
        _undoRecords.value = listOf(record) + filtered
    }

    /**
     * 消费或关闭指定的撤销记录
     */
    fun removeRecord(eventId: Long): UndoRecord? {
        val current = _undoRecords.value
        val target = current.find { it.eventId == eventId } ?: return null
        _undoRecords.value = current.filter { it.eventId != eventId }
        return target
    }

    /**
     * 使特定任务的旧撤销失效
     */
    fun invalidateTask(taskId: Int) {
        _undoRecords.value = _undoRecords.value.filter { it.taskId != taskId }
    }

    /**
     * 使特定清单相关的旧撤销失效
     */
    fun invalidateList(listId: Int) {
        _undoRecords.value = _undoRecords.value.filter { it.listId != listId }
    }

    /**
     * 清空所有旧撤销记录
     */
    fun clearAll() {
        _undoRecords.value = emptyList()
    }
}
