package com.example.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.StringReader
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonBackupCodecTest {

    @Test
    fun testValidRoundTrip() {
        val original = BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = 2,
            exportedAt = "2026-09-12T12:34:56Z",
            sourceAppVersion = "2.0.0",
            lists = listOf(
                TaskListBackupDto(
                    id = 1,
                    name = "工作 💻",
                    themeColor = 0xFF1E88E5L,
                    displayOrder = 1,
                    completionMode = 1,
                    timestamp = 1700000000000L,
                    showInSummary = true
                ),
                TaskListBackupDto(
                    id = 2,
                    name = "生活 & 购物 🛒\n换行清单",
                    themeColor = 0xFF43A047L,
                    displayOrder = 2,
                    completionMode = 0,
                    timestamp = 1700000001000L,
                    showInSummary = false
                )
            ),
            tasks = listOf(
                TaskBackupDto(
                    id = 101,
                    listId = 1,
                    title = "完成 2.0.0 实施",
                    content = "编写详细测试用例\n支持 Emoji 🚀 与 \"双引号\"",
                    isCompleted = false,
                    isFlagged = true,
                    displayOrder = 1,
                    timestamp = 1700000002000L
                ),
                TaskBackupDto(
                    id = 102,
                    listId = 2,
                    title = "独立清单任务",
                    content = "",
                    isCompleted = true,
                    isFlagged = false,
                    displayOrder = 2,
                    timestamp = 1700000003000L
                )
            )
        )

        val writer = StringWriter()
        JsonBackupCodec.writeBackup(original, writer)
        val jsonOutput = writer.toString()

        val parsed = JsonBackupCodec.readBackup(StringReader(jsonOutput))

        assertEquals(original.format, parsed.format)
        assertEquals(original.formatVersion, parsed.formatVersion)
        assertEquals(original.exportedAt, parsed.exportedAt)
        assertEquals(original.sourceAppVersion, parsed.sourceAppVersion)

        assertEquals(2, parsed.lists.size)
        assertEquals(original.lists[0], parsed.lists[0])
        assertEquals(original.lists[1], parsed.lists[1])

        assertEquals(2, parsed.tasks.size)
        assertEquals(original.tasks[0], parsed.tasks[0])
        assertEquals(original.tasks[1], parsed.tasks[1])
    }

    @Test
    fun testV1BackwardCompatibility() {
        val v1Json = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 1,
                "exportedAt": "2026-09-11T12:00:00Z",
                "sourceAppVersion": "1.5.0",
                "lists": [
                    {"id": 1, "name": "旧清单", "themeColor": 0, "displayOrder": 1, "completionMode": 0, "timestamp": 100}
                ],
                "tasks": [
                    {"id": 10, "listId": 1, "title": "旧任务", "content": "", "isCompleted": false, "isFlagged": false, "displayOrder": 1, "timestamp": 100}
                ]
            }
        """.trimIndent()

        val parsed = JsonBackupCodec.readBackup(StringReader(v1Json))
        assertEquals(1, parsed.formatVersion)
        assertEquals(1, parsed.lists.size)
        assertTrue("v1 清单导入时 showInSummary 默认 true", parsed.lists[0].showInSummary)
        assertEquals(1, parsed.tasks.size)
        assertEquals(1, parsed.tasks[0].listId)
    }

    @Test
    fun testNullListIdRejected() {
        val nullListIdJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [
                    {"id": 1, "name": "清单", "themeColor": 0, "displayOrder": 1, "completionMode": 0, "timestamp": 100, "showInSummary": true}
                ],
                "tasks": [
                    {"id": 10, "listId": null, "title": "孤立任务", "content": "", "isCompleted": false, "isFlagged": false, "displayOrder": 1, "timestamp": 100}
                ]
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(nullListIdJson))
        }
        assertTrue(ex.message!!.contains("缺少有效的 listId 归属"))
    }

    @Test
    fun testEmptyDataRoundTrip() {
        val original = BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = 2,
            exportedAt = "2026-09-12T12:00:00Z",
            sourceAppVersion = "2.0.0",
            lists = emptyList(),
            tasks = emptyList()
        )

        val writer = StringWriter()
        JsonBackupCodec.writeBackup(original, writer)
        val parsed = JsonBackupCodec.readBackup(StringReader(writer.toString()))

        assertEquals(0, parsed.lists.size)
        assertEquals(0, parsed.tasks.size)
    }

    @Test
    fun testInvalidFormatOrVersionRejected() {
        val invalidFormatJson = """
            {
                "format": "com.other.format",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [],
                "tasks": []
            }
        """.trimIndent()

        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(invalidFormatJson))
        }
        assertTrue(ex1.message!!.contains("不支持的文件格式"))

        val invalidVersionJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 99,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [],
                "tasks": []
            }
        """.trimIndent()

        val ex2 = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(invalidVersionJson))
        }
        assertTrue(ex2.message!!.contains("不支持的备份版本"))
    }

    @Test
    fun testMissingHeaderFieldsRejected() {
        val missingVersionJson = """
            {
                "format": "com.aistudio.todo.backup",
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [],
                "tasks": []
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(missingVersionJson))
        }
        assertTrue(ex.message!!.contains("缺少 formatVersion"))
    }

    @Test
    fun testDuplicateTopLevelKeysRejected() {
        val duplicateKeyJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [],
                "tasks": [],
                "format": "com.aistudio.todo.backup"
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(duplicateKeyJson))
        }
        assertTrue(ex.message!!.contains("存在重复字段: format"))
    }

    @Test
    fun testDuplicateListIdRejected() {
        val duplicateListIdJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [
                    {"id": 1, "name": "列表A", "themeColor": 0, "displayOrder": 1, "completionMode": 0, "timestamp": 100, "showInSummary": true},
                    {"id": 1, "name": "列表B", "themeColor": 0, "displayOrder": 2, "completionMode": 0, "timestamp": 200, "showInSummary": true}
                ],
                "tasks": []
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(duplicateListIdJson))
        }
        assertTrue(ex.message!!.contains("清单中存在重复 ID: 1"))
    }

    @Test
    fun testDuplicateTaskIdRejected() {
        val duplicateTaskIdJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [
                    {"id": 1, "name": "列表A", "themeColor": 0, "displayOrder": 1, "completionMode": 0, "timestamp": 100, "showInSummary": true}
                ],
                "tasks": [
                    {"id": 10, "listId": 1, "title": "任务1", "content": "", "isCompleted": false, "isFlagged": false, "displayOrder": 1, "timestamp": 100},
                    {"id": 10, "listId": 1, "title": "任务2", "content": "", "isCompleted": false, "isFlagged": false, "displayOrder": 2, "timestamp": 200}
                ]
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(duplicateTaskIdJson))
        }
        assertTrue(ex.message!!.contains("待办中存在重复 ID: 10"))
    }

    @Test
    fun testOrphanTaskForeignKeyRejected() {
        val orphanTaskJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [
                    {"id": 1, "name": "列表A", "themeColor": 0, "displayOrder": 1, "completionMode": 0, "timestamp": 100, "showInSummary": true}
                ],
                "tasks": [
                    {"id": 10, "listId": 999, "title": "悬空任务", "content": "", "isCompleted": false, "isFlagged": false, "displayOrder": 1, "timestamp": 100}
                ]
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(orphanTaskJson))
        }
        assertTrue(ex.message!!.contains("引用了不存在的清单 ID: 999"))
    }

    @Test
    fun testTrailingGarbageRejected() {
        val trailingGarbageJson = """
            {
                "format": "com.aistudio.todo.backup",
                "formatVersion": 2,
                "exportedAt": "2026-09-12T12:00:00Z",
                "sourceAppVersion": "2.0.0",
                "lists": [],
                "tasks": []
            }
            {"extra": "data"}
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(trailingGarbageJson))
        }
        assertTrue(ex.message!!.contains("末尾存在多余数据"))
    }

    @Test
    fun testFileSizeLimitEnforcement() {
        val tempFile = File.createTempFile("test_limit", ".json")
        try {
            tempFile.writeText("a".repeat(1024))
            val dummyFile = object : File(tempFile.absolutePath) {
                override fun length(): Long = BackupData.MAX_FILE_SIZE_BYTES + 1L
            }
            val ex = assertThrows(IllegalArgumentException::class.java) {
                JsonBackupCodec.readBackup(dummyFile)
            }
            assertTrue(ex.message!!.contains("超过上限 32 MiB"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testV1MissingListsRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","tasks":[]}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 lists 清单集合"))
    }

    @Test
    fun testV1MissingTasksRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[]}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 tasks 待办集合"))
    }

    @Test
    fun testV1MissingBothListsAndTasksRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4"}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 lists 清单集合"))
    }

    @Test
    fun testV2MissingListsRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","tasks":[]}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 lists 清单集合"))
    }

    @Test
    fun testV2MissingTasksRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[]}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 tasks 待办集合"))
    }

    @Test
    fun testV2MissingBothListsAndTasksRejected() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4"}
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonBackupCodec.readBackup(StringReader(json))
        }
        assertTrue(ex.message!!.contains("缺少 lists 清单集合"))
    }

    @Test
    fun testExplicitEmptyArraysAccepted() {
        val json = """
            {"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[],"tasks":[]}
        """.trimIndent()
        val backup = JsonBackupCodec.readBackup(StringReader(json))
        assertEquals(0, backup.lists.size)
        assertEquals(0, backup.tasks.size)
    }
}
