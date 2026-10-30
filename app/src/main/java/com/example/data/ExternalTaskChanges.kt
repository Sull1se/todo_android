package com.example.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 应用页面之外（桌面小卡片）对单个任务的写入通知。
 * 同进程的 ViewModel 订阅后使该任务的旧撤销记录失效，避免撤销覆盖卡片上的操作。
 */
object ExternalTaskChanges {
    private val _taskIds = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val taskIds: SharedFlow<Int> = _taskIds.asSharedFlow()

    fun notifyChanged(taskId: Int) {
        _taskIds.tryEmit(taskId)
    }
}
