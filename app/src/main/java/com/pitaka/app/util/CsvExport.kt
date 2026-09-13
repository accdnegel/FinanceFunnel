package com.pitaka.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pitaka.app.data.LedgerEntry
import com.pitaka.app.data.Pitaka
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

/** Builds a CSV of every ledger entry, resolving Pitaka/Goal names for readability. */
fun buildLedgerCsv(entries: List<LedgerEntry>, pitakas: List<Pitaka>, goalNamesById: Map<Long, String>): String {
    fun pitakaName(id: Long?) = pitakas.find { it.id == id }?.name ?: ""
    fun goalName(id: Long?) = id?.let { goalNamesById[it] } ?: ""

    val header = listOf(
        "Date", "Type", "Name", "Amount", "Category", "Pitaka", "From Pitaka", "To Pitaka", "Goal"
    ).joinToString(",")

    val rows = entries.map { e ->
        val pitaka = pitakaName(e.pitakaId)
        val from = pitakaName(e.fromPitakaId)
        val to = pitakaName(e.toPitakaId)
        val goal = goalName(e.goalId)
        listOf(
            csvDateFormat.format(java.util.Date(e.date)),
            e.type.name,
            csvEscape(e.name),
            e.amount.toString(),
            csvEscape(e.category ?: ""),
            csvEscape(pitaka),
            csvEscape(from),
            csvEscape(to),
            csvEscape(goal)
        ).joinToString(",")
    }

    return (listOf(header) + rows).joinToString("\n")
}

private fun csvEscape(value: String): String {
    return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value
}

/** Writes the CSV to a cache file and launches the system share sheet for it. */
fun exportAndShareCsv(context: Context, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "pitaka_export_${System.currentTimeMillis()}.csv")
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Pitaka data"))
}
