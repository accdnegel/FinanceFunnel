package com.pitaka.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pitaka.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DatabaseBackup {
    private const val DATABASE_NAME = "pitaka.db"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    suspend fun create(context: Context): File = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(context)
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

        val source = context.getDatabasePath(DATABASE_NAME)
        require(source.exists()) { "Pitaka database was not found." }
        val backupDirectory = File(context.cacheDir, "backups").apply { mkdirs() }
        val destination = File(backupDirectory, "pitaka-backup-${System.currentTimeMillis()}.db")
        source.copyTo(destination, overwrite = true)
        destination
    }

    fun share(context: Context, backup: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Pitaka backup"))
    }

    suspend fun restore(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val temporary = File(context.cacheDir, "pitaka-restore-${System.currentTimeMillis()}.db")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open the selected backup." }
            temporary.outputStream().use(input::copyTo)
        }
        val header = temporary.inputStream().use { input ->
            ByteArray(SQLITE_HEADER.length).also { bytes ->
                require(input.read(bytes) == bytes.size) { "The selected file is not a valid database backup." }
            }.toString(Charsets.US_ASCII)
        }
        require(header == SQLITE_HEADER) { "The selected file is not a valid SQLite database." }

        AppDatabase.closeInstance()
        val destination = context.getDatabasePath(DATABASE_NAME)
        destination.parentFile?.mkdirs()
        File(destination.path + "-wal").delete()
        File(destination.path + "-shm").delete()
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
    }
}
