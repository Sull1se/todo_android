package com.example.data.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class DataTransferManager(private val context: Context) {

    suspend fun exportToUri(backup: BackupData, targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "todo_export_temp_${System.currentTimeMillis()}.json")
        try {
            // 1. 编码写入私有临时文件
            JsonBackupCodec.writeBackup(backup, tempFile)

            // 2. 自校验该文件
            val selfValidated = JsonBackupCodec.readBackup(tempFile)
            if (selfValidated.lists.size != backup.lists.size || selfValidated.tasks.size != backup.tasks.size) {
                throw IllegalStateException("自校验数据项数量不匹配")
            }

            // 3. 流式写出到目标 URI
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            } ?: throw IllegalStateException("无法打开目标文件的输出流")

            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun readAndValidateFromUri(sourceUri: Uri): Result<Pair<BackupData, File>> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "todo_import_temp_${System.currentTimeMillis()}.json")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    copyStreamWithLimit(inputStream, outputStream, BackupData.MAX_FILE_SIZE_BYTES)
                }
            } ?: throw IllegalStateException("无法打开所选文件的输入流")

            val backupData = JsonBackupCodec.readBackup(tempFile)
            Result.success(Pair(backupData, tempFile))
        } catch (e: Throwable) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    fun cleanTempFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun copyStreamWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(8192)
        var totalBytes = 0L
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            totalBytes += bytesRead
            if (totalBytes > maxBytes) {
                throw IllegalArgumentException("文件大小超过上限 32 MiB")
            }
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
        return totalBytes
    }
}
