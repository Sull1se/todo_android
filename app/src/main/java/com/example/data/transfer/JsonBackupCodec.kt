package com.example.data.transfer

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.Reader
import java.io.Writer

object JsonBackupCodec {

    fun writeBackup(backup: BackupData, file: File) {
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeBackup(backup, writer)
        }
    }

    fun readBackup(file: File): BackupData {
        if (file.length() > BackupData.MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("文件大小超过上限 32 MiB")
        }
        return file.bufferedReader(Charsets.UTF_8).use { reader ->
            readBackup(reader)
        }
    }

    fun writeBackup(backup: BackupData, writer: Writer) {
        val jsonWriter = JsonWriter(writer)
        jsonWriter.setIndent("  ")
        jsonWriter.beginObject()

        jsonWriter.name("format").value(backup.format)
        jsonWriter.name("formatVersion").value(backup.formatVersion)
        jsonWriter.name("exportedAt").value(backup.exportedAt)
        jsonWriter.name("sourceAppVersion").value(backup.sourceAppVersion)

        // Lists
        jsonWriter.name("lists").beginArray()
        for (list in backup.lists) {
            jsonWriter.beginObject()
            jsonWriter.name("id").value(list.id)
            jsonWriter.name("name").value(list.name)
            jsonWriter.name("themeColor").value(list.themeColor)
            jsonWriter.name("displayOrder").value(list.displayOrder)
            jsonWriter.name("completionMode").value(list.completionMode)
            jsonWriter.name("timestamp").value(list.timestamp)
            jsonWriter.name("showInSummary").value(list.showInSummary)
            jsonWriter.endObject()
        }
        jsonWriter.endArray()

        // Tasks
        jsonWriter.name("tasks").beginArray()
        for (task in backup.tasks) {
            jsonWriter.beginObject()
            jsonWriter.name("id").value(task.id)
            jsonWriter.name("listId").value(task.listId)
            jsonWriter.name("title").value(task.title)
            jsonWriter.name("content").value(task.content)
            jsonWriter.name("isCompleted").value(task.isCompleted)
            jsonWriter.name("isFlagged").value(task.isFlagged)
            jsonWriter.name("displayOrder").value(task.displayOrder)
            jsonWriter.name("timestamp").value(task.timestamp)
            jsonWriter.endObject()
        }
        jsonWriter.endArray()

        jsonWriter.endObject()
        jsonWriter.flush()
    }

    fun readBackup(reader: Reader): BackupData {
        val jsonReader = JsonReader(reader)
        try {
            var format: String? = null
            var formatVersion: Int? = null
            var exportedAt: String? = null
            var sourceAppVersion: String? = null
            val rawLists = mutableListOf<RawTaskListDto>()
            val rawTasks = mutableListOf<RawTaskDto>()

            val seenHeaderKeys = mutableSetOf<String>()

            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val key = jsonReader.nextName()
                if (!seenHeaderKeys.add(key)) {
                    throw IllegalArgumentException("备份文件中存在重复字段: $key")
                }
                when (key) {
                    "format" -> format = jsonReader.nextString()
                    "formatVersion" -> formatVersion = jsonReader.nextInt()
                    "exportedAt" -> exportedAt = jsonReader.nextString()
                    "sourceAppVersion" -> sourceAppVersion = jsonReader.nextString()
                    "lists" -> {
                        jsonReader.beginArray()
                        val seenListIds = mutableSetOf<Int>()
                        while (jsonReader.hasNext()) {
                            if (rawLists.size >= BackupData.MAX_LIST_COUNT) {
                                throw IllegalArgumentException("清单数量超过上限 (${BackupData.MAX_LIST_COUNT})")
                            }
                            val listDto = readRawTaskListDto(jsonReader)
                            if (listDto.id <= 0) {
                                throw IllegalArgumentException("清单 ID 必须为正整数，实际为: ${listDto.id}")
                            }
                            if (!seenListIds.add(listDto.id)) {
                                throw IllegalArgumentException("清单中存在重复 ID: ${listDto.id}")
                            }
                            if (listDto.name.isBlank()) {
                                throw IllegalArgumentException("清单名称不能为空")
                            }
                            if (listDto.completionMode !in listOf(0, 1)) {
                                throw IllegalArgumentException("清单 completionMode 必须为 0 或 1，实际为: ${listDto.completionMode}")
                            }
                            rawLists.add(listDto)
                        }
                        jsonReader.endArray()
                    }
                    "tasks" -> {
                        jsonReader.beginArray()
                        val seenTaskIds = mutableSetOf<Int>()
                        while (jsonReader.hasNext()) {
                            if (rawTasks.size >= BackupData.MAX_TASK_COUNT) {
                                throw IllegalArgumentException("待办数量超过上限 (${BackupData.MAX_TASK_COUNT})")
                            }
                            val taskDto = readRawTaskDto(jsonReader)
                            if (taskDto.id <= 0) {
                                throw IllegalArgumentException("待办 ID 必须为正整数，实际为: ${taskDto.id}")
                            }
                            if (!seenTaskIds.add(taskDto.id)) {
                                throw IllegalArgumentException("待办中存在重复 ID: ${taskDto.id}")
                            }
                            if (taskDto.title.isBlank()) {
                                throw IllegalArgumentException("待办标题不能为空")
                            }
                            rawTasks.add(taskDto)
                        }
                        jsonReader.endArray()
                    }
                    else -> {
                        throw IllegalArgumentException("备份文件中存在未知顶层字段: $key")
                    }
                }
            }
            jsonReader.endObject()

            // 校验文件结束
            val isEnd = try {
                jsonReader.peek() == JsonToken.END_DOCUMENT
            } catch (e: android.util.MalformedJsonException) {
                throw IllegalArgumentException("JSON 文件末尾存在多余数据", e)
            }
            if (!isEnd) {
                throw IllegalArgumentException("JSON 文件末尾存在多余数据")
            }

            // 校验必需字段
            if (format == null) throw IllegalArgumentException("缺少 format 标识")
            if (format != BackupData.BACKUP_FORMAT) {
                throw IllegalArgumentException("不支持的文件格式: $format，预期为 ${BackupData.BACKUP_FORMAT}")
            }
            if (formatVersion == null) throw IllegalArgumentException("缺少 formatVersion 版本号")
            if (formatVersion !in listOf(1, 2)) {
                throw IllegalArgumentException("不支持的备份版本: $formatVersion，当前支持版本为 1 或 2")
            }
            if (exportedAt.isNullOrBlank()) throw IllegalArgumentException("缺少 exportedAt 导出时间")
            if (sourceAppVersion.isNullOrBlank()) throw IllegalArgumentException("缺少 sourceAppVersion 应用版本")
            if (!seenHeaderKeys.contains("lists")) throw IllegalArgumentException("缺少 lists 清单集合")
            if (!seenHeaderKeys.contains("tasks")) throw IllegalArgumentException("缺少 tasks 待办集合")

            // 根据 formatVersion 校验与构建 TaskListBackupDto
            val lists = rawLists.map { raw ->
                val showInSummary = if (formatVersion == 1) {
                    raw.showInSummary ?: true
                } else {
                    raw.showInSummary ?: throw IllegalArgumentException("清单缺少 showInSummary")
                }
                TaskListBackupDto(
                    id = raw.id,
                    name = raw.name,
                    themeColor = raw.themeColor,
                    displayOrder = raw.displayOrder,
                    completionMode = raw.completionMode,
                    timestamp = raw.timestamp,
                    showInSummary = showInSummary
                )
            }

            // 校验外键引用一致性与非空 listId
            val validListIds = lists.map { it.id }.toSet()
            val tasks = rawTasks.map { raw ->
                val listId = raw.listId ?: throw IllegalArgumentException("待办 '${raw.title}' 缺少有效的 listId 归属")
                if (listId !in validListIds) {
                    throw IllegalArgumentException("待办 '${raw.title}' 引用了不存在的清单 ID: $listId")
                }
                TaskBackupDto(
                    id = raw.id,
                    listId = listId,
                    title = raw.title,
                    content = raw.content,
                    isCompleted = raw.isCompleted,
                    isFlagged = raw.isFlagged,
                    displayOrder = raw.displayOrder,
                    timestamp = raw.timestamp
                )
            }

            return BackupData(
                format = format,
                formatVersion = formatVersion,
                exportedAt = exportedAt,
                sourceAppVersion = sourceAppVersion,
                lists = lists,
                tasks = tasks
            )
        } catch (e: android.util.MalformedJsonException) {
            throw IllegalArgumentException("JSON 语法解析错误: ${e.message}", e)
        }
    }

    private data class RawTaskListDto(
        val id: Int,
        val name: String,
        val themeColor: Long,
        val displayOrder: Int,
        val completionMode: Int,
        val timestamp: Long,
        val showInSummary: Boolean?
    )

    private data class RawTaskDto(
        val id: Int,
        val listId: Int?,
        val title: String,
        val content: String,
        val isCompleted: Boolean,
        val isFlagged: Boolean,
        val displayOrder: Int,
        val timestamp: Long
    )

    private fun readRawTaskListDto(reader: JsonReader): RawTaskListDto {
        var id: Int? = null
        var name: String? = null
        var themeColor: Long? = null
        var displayOrder: Int? = null
        var completionMode: Int? = null
        var timestamp: Long? = null
        var showInSummary: Boolean? = null

        val seenKeys = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (!seenKeys.add(key)) {
                throw IllegalArgumentException("清单对象中存在重复字段: $key")
            }
            when (key) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                "themeColor" -> themeColor = reader.nextLong()
                "displayOrder" -> displayOrder = reader.nextInt()
                "completionMode" -> completionMode = reader.nextInt()
                "timestamp" -> timestamp = reader.nextLong()
                "showInSummary" -> showInSummary = reader.nextBoolean()
                else -> throw IllegalArgumentException("清单对象中存在未知字段: $key")
            }
        }
        reader.endObject()

        return RawTaskListDto(
            id = id ?: throw IllegalArgumentException("清单缺少 id"),
            name = name ?: throw IllegalArgumentException("清单缺少 name"),
            themeColor = themeColor ?: throw IllegalArgumentException("清单缺少 themeColor"),
            displayOrder = displayOrder ?: throw IllegalArgumentException("清单缺少 displayOrder"),
            completionMode = completionMode ?: throw IllegalArgumentException("清单缺少 completionMode"),
            timestamp = timestamp ?: throw IllegalArgumentException("清单缺少 timestamp"),
            showInSummary = showInSummary
        )
    }

    private fun readRawTaskDto(reader: JsonReader): RawTaskDto {
        var id: Int? = null
        var listId: Int? = null
        var title: String? = null
        var content: String? = null
        var isCompleted: Boolean? = null
        var isFlagged: Boolean? = null
        var displayOrder: Int? = null
        var timestamp: Long? = null

        val seenKeys = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (!seenKeys.add(key)) {
                throw IllegalArgumentException("待办对象中存在重复字段: $key")
            }
            when (key) {
                "id" -> id = reader.nextInt()
                "listId" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        listId = null
                    } else {
                        listId = reader.nextInt()
                    }
                }
                "title" -> title = reader.nextString()
                "content" -> content = reader.nextString()
                "isCompleted" -> isCompleted = reader.nextBoolean()
                "isFlagged" -> isFlagged = reader.nextBoolean()
                "displayOrder" -> displayOrder = reader.nextInt()
                "timestamp" -> timestamp = reader.nextLong()
                else -> throw IllegalArgumentException("待办对象中存在未知字段: $key")
            }
        }
        reader.endObject()

        return RawTaskDto(
            id = id ?: throw IllegalArgumentException("待办缺少 id"),
            listId = listId,
            title = title ?: throw IllegalArgumentException("待办缺少 title"),
            content = content ?: "",
            isCompleted = isCompleted ?: false,
            isFlagged = isFlagged ?: false,
            displayOrder = displayOrder ?: 0,
            timestamp = timestamp ?: throw IllegalArgumentException("待办缺少 timestamp")
        )
    }
}
