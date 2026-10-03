package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskList::class, Task::class, AppMetadata::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isFlagged INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 验证不存在 listId IS NULL 或无效外键
                val nullCheckCursor = db.query("SELECT COUNT(*) FROM tasks WHERE listId IS NULL")
                var nullCount = 0
                if (nullCheckCursor.moveToFirst()) {
                    nullCount = nullCheckCursor.getInt(0)
                }
                nullCheckCursor.close()
                if (nullCount > 0) {
                    throw IllegalStateException("数据库存在 $nullCount 条无归属清单待办 (listId IS NULL)，迁移前置条件不满足，中止升级")
                }

                val orphanCheckCursor = db.query(
                    "SELECT COUNT(*) FROM tasks WHERE listId NOT IN (SELECT id FROM task_lists)"
                )
                var orphanCount = 0
                if (orphanCheckCursor.moveToFirst()) {
                    orphanCount = orphanCheckCursor.getInt(0)
                }
                orphanCheckCursor.close()
                if (orphanCount > 0) {
                    throw IllegalStateException("数据库存在 $orphanCount 条孤立待办，引用的清单 ID 不存在，中止升级")
                }

                // 2. 为 task_lists 表增加 showInSummary，默认值为 1
                db.execSQL("ALTER TABLE `task_lists` ADD COLUMN `showInSummary` INTEGER NOT NULL DEFAULT 1")

                // 3. 创建符合目标 schema 6 的 tasks 临时表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`listId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`isCompleted` INTEGER NOT NULL, " +
                        "`displayOrder` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`isFlagged` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks_new` (`listId`)")

                // 4. 复制全部任务，并对 completionMode=1 且 isCompleted=1 的项清除旗帜 (isFlagged = 0)
                db.execSQL(
                    "INSERT INTO `tasks_new` (`id`, `listId`, `title`, `content`, `isCompleted`, `displayOrder`, `timestamp`, `isFlagged`) " +
                        "SELECT t.`id`, t.`listId`, t.`title`, t.`content`, t.`isCompleted`, t.`displayOrder`, t.`timestamp`, " +
                        "CASE WHEN t.`isCompleted` = 1 AND l.`completionMode` = 1 THEN 0 ELSE t.`isFlagged` END " +
                        "FROM `tasks` t INNER JOIN `task_lists` l ON t.`listId` = l.`id`"
                )

                // 5. 替换旧 tasks 表
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")

                // 6. 创建 app_metadata 表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_metadata` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`summaryOrderInitialized` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("INSERT OR IGNORE INTO `app_metadata` (`id`, `summaryOrderInitialized`) VALUES (1, 0)")
            }
        }

        fun databaseBuilder(context: Context, databaseName: String = "todo_database"): Builder<AppDatabase> {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                databaseName
            ).addMigrations(MIGRATION_4_5, MIGRATION_5_6)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = databaseBuilder(context).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
