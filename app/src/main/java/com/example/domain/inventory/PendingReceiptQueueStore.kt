package com.example.domain.inventory

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PendingReceiptAttachment(
    val fileName: String,
    val mimeType: String
)

data class PendingReceiptIntake(
    val id: String = UUID.randomUUID().toString(),
    val sourceLabel: String,
    val sharedText: String = "",
    val attachments: List<PendingReceiptAttachment> = emptyList(),
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 1
)

/**
 * Persistent local queue for shared PDF/photo raw-material receipts so if all AI models
 * or network connections fail temporarily, receipts are never lost and can be retried automatically
 * or manually via "Retry AI Scan".
 */
object PendingReceiptQueueStore {
    private const val PREFS_NAME = "filtr_pending_receipts"
    private const val KEY_QUEUE_JSON = "queued_receipts_v1"
    private const val DIR_NAME = "pending_receipt_files"

    fun enqueueOrUpdate(
        context: Context,
        attachments: List<Pair<ByteArray, String>>,
        sharedText: String,
        sourceLabel: String,
        lastError: String,
        existingId: String? = null
    ): PendingReceiptIntake {
        val currentList = loadAll(context).toMutableList()
        val id = existingId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        val existingIndex = currentList.indexOfFirst { it.id == id }
        val savedAttachments = if (existingIndex >= 0 && attachments.isEmpty()) {
            currentList[existingIndex].attachments
        } else {
            attachments.take(3).mapIndexed { index, (bytes, mimeType) ->
                val ext = when {
                    mimeType == "application/pdf" -> "pdf"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val fileName = "${id}_$index.$ext"
                File(dir, fileName).writeBytes(bytes)
                PendingReceiptAttachment(fileName = fileName, mimeType = mimeType)
            }
        }

        val entry = PendingReceiptIntake(
            id = id,
            sourceLabel = sourceLabel.trim().ifBlank { "Shared Receipt" },
            sharedText = sharedText,
            attachments = savedAttachments,
            lastError = lastError.trim(),
            createdAt = if (existingIndex >= 0) currentList[existingIndex].createdAt else System.currentTimeMillis(),
            attemptCount = if (existingIndex >= 0) currentList[existingIndex].attemptCount + 1 else 1
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = entry
        } else {
            currentList.add(0, entry)
        }
        saveList(context, currentList.take(20))
        return entry
    }

    fun loadAll(context: Context): List<PendingReceiptIntake> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_QUEUE_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<PendingReceiptIntake>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                if (id.isBlank()) continue
                val attArr = obj.optJSONArray("attachments") ?: JSONArray()
                val attachments = mutableListOf<PendingReceiptAttachment>()
                for (j in 0 until attArr.length()) {
                    val attObj = attArr.optJSONObject(j) ?: continue
                    val fileName = attObj.optString("fileName", "").trim()
                    val mimeType = attObj.optString("mimeType", "").trim()
                    if (fileName.isNotBlank() && mimeType.isNotBlank()) {
                        attachments.add(PendingReceiptAttachment(fileName, mimeType))
                    }
                }
                result.add(
                    PendingReceiptIntake(
                        id = id,
                        sourceLabel = obj.optString("sourceLabel", "Shared Receipt"),
                        sharedText = obj.optString("sharedText", ""),
                        attachments = attachments,
                        lastError = obj.optString("lastError", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        attemptCount = obj.optInt("attemptCount", 1)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadAttachmentBytes(context: Context, intake: PendingReceiptIntake): List<Pair<ByteArray, String>> {
        val dir = File(context.filesDir, DIR_NAME)
        return intake.attachments.mapNotNull { att ->
            val file = File(dir, att.fileName)
            if (file.exists() && file.length() > 0L) {
                try {
                    file.readBytes() to att.mimeType
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    fun remove(context: Context, id: String) {
        if (id.isBlank()) return
        val currentList = loadAll(context)
        val target = currentList.firstOrNull { it.id == id }
        if (target != null) {
            val dir = File(context.filesDir, DIR_NAME)
            target.attachments.forEach { att ->
                runCatching { File(dir, att.fileName).delete() }
            }
        }
        val remaining = currentList.filterNot { it.id == id }
        saveList(context, remaining)
    }

    private fun saveList(context: Context, list: List<PendingReceiptIntake>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("sourceLabel", item.sourceLabel)
                put("sharedText", item.sharedText)
                put("lastError", item.lastError)
                put("createdAt", item.createdAt)
                put("attemptCount", item.attemptCount)
                put("attachments", JSONArray().apply {
                    item.attachments.forEach { att ->
                        put(JSONObject().apply {
                            put("fileName", att.fileName)
                            put("mimeType", att.mimeType)
                        })
                    }
                })
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE_JSON, arr.toString())
            .apply()
    }
}
