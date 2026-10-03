package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val themeColor: Long = 0xFFD3E3FDL,
    val displayOrder: Int = 0,
    val completionMode: Int = 0, // 0 = Keep position, 1 = Move to bottom
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val showInSummary: Boolean = true
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val listId: Int,
    val title: String,
    val content: String,
    val isCompleted: Boolean = false,
    val displayOrder: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isFlagged: Boolean = false
)

@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey val id: Int = 1,
    val summaryOrderInitialized: Boolean = false
)
