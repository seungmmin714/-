package ai.moa.app.storage

import android.content.Context
import android.util.AtomicFile
import ai.moa.core.FamilyCard
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class SavedFamilyCard(val id: String, val createdAt: Long, val card: FamilyCard)

/**
 * Explicit-save-only, app-private storage. No session data is persisted automatically.
 * A new save retains the newest 100 cards and removes older cards. Backup is disabled
 * by the app manifest; this class never accesses an external directory or a network.
 */
class FamilyCardStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(file)

    fun all(): List<SavedFamilyCard> = synchronized(storageLock) { readCards() }

    /** Throws if input is invalid or writing fails; callers must not show a false success. */
    fun save(card: FamilyCard): SavedFamilyCard = synchronized(storageLock) {
        require(validCard(card)) { "저장할 대화 카드의 내용이 올바르지 않아요." }
        val saved = SavedFamilyCard(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            card = card.copy(choices = card.choices.toList()),
        )
        // Keeping the new entry first also gives deterministic order for equal timestamps.
        val cards = (listOf(saved) + readCards()).take(MAX_CARDS)
        writeCards(cards)
        saved
    }

    fun delete(id: String) = synchronized(storageLock) {
        val current = readCards()
        val remaining = current.filterNot { it.id == id }
        if (remaining.size != current.size) writeCards(remaining)
    }

    fun clear() = synchronized(storageLock) {
        atomicFile.delete()
        if (listOf(file, File(file.path + ".bak"), File(file.path + ".new")).any { it.exists() }) {
            throw IOException("저장된 카드를 지우지 못했어요. 다시 시도해 주세요.")
        }
    }

    private fun readCards(): List<SavedFamilyCard> {
        return try {
            val text = atomicFile.openRead().use { input ->
                if (input.channel.size() > MAX_FILE_BYTES) return emptyList()
                input.readBytes().toString(Charsets.UTF_8)
            }
            val document = JSONObject(text)
            if (document.optInt("version", -1) != SCHEMA_VERSION) return emptyList()
            val entries = document.optJSONArray("cards") ?: return emptyList()
            // Reject oversized documents instead of processing unbounded corrupt arrays.
            if (entries.length() > MAX_CARDS) return emptyList()
            val result = mutableListOf<SavedFamilyCard>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                parseEntry(entry)?.let { result += it }
            }
            result.distinctBy { it.id }.sortedByDescending { it.createdAt }
        } catch (_: IOException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun parseEntry(entry: JSONObject): SavedFamilyCard? {
        val id = entry.opt("id") as? String ?: return null
        if (id.length != 36 || runCatching { UUID.fromString(id) }.isFailure) return null
        val timestamp = entry.opt("createdAt") as? Number ?: return null
        val createdAt = timestamp.toLong()
        if (createdAt < 0 || timestamp.toDouble() != createdAt.toDouble()) return null
        val question = entry.opt("question") as? String ?: return null
        val subject = entry.opt("subject") as? String ?: return null
        val openQuestion = entry.opt("openQuestion") as? String ?: return null
        val handoff = entry.opt("isHandoff") as? Boolean ?: return null
        val choicesJson = entry.optJSONArray("choices") ?: return null
        if (choicesJson.length() > MAX_CHOICES) return null
        val choices = (0 until choicesJson.length()).map { index ->
            choicesJson.opt(index) as? String ?: return null
        }
        val card = FamilyCard(question, subject, choices, openQuestion, handoff)
        if (!validCard(card)) return null
        return SavedFamilyCard(id, createdAt, card)
    }

    private fun writeCards(cards: List<SavedFamilyCard>) {
        val entries = JSONArray()
        cards.forEach { saved ->
            entries.put(JSONObject().apply {
                put("id", saved.id)
                put("createdAt", saved.createdAt)
                put("question", saved.card.question)
                put("subject", saved.card.subject)
                put("choices", JSONArray(saved.card.choices))
                put("openQuestion", saved.card.openQuestion)
                put("isHandoff", saved.card.isHandoff)
            })
        }
        val bytes = JSONObject().put("version", SCHEMA_VERSION).put("cards", entries)
            .toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_FILE_BYTES)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
            output = null
            // AtomicFile can log a rename failure without throwing. Verify persisted bytes.
            val persisted = atomicFile.openRead().use { it.readBytes() }
            if (!persisted.contentEquals(bytes)) throw IOException("카드 저장을 확인하지 못했어요.")
        } catch (error: Exception) {
            output?.let { atomicFile.failWrite(it) }
            throw error
        }
    }

    private fun validCard(card: FamilyCard): Boolean =
        card.question.isNotBlank() && card.question.length <= MAX_QUESTION_LENGTH &&
            card.subject.isNotBlank() && card.subject.length <= MAX_LABEL_LENGTH &&
            card.openQuestion.isNotBlank() && card.openQuestion.length <= MAX_OPEN_QUESTION_LENGTH &&
            card.choices.size <= MAX_CHOICES &&
            card.choices.all { it.isNotBlank() && it.length <= MAX_LABEL_LENGTH }

    companion object {
        internal const val FILE_NAME = "moa_family_cards.json"
        const val MAX_CARDS = 100
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 512_000
        private const val MAX_QUESTION_LENGTH = 200
        private const val MAX_OPEN_QUESTION_LENGTH = 240
        private const val MAX_LABEL_LENGTH = 80
        private const val MAX_CHOICES = 2
        private val storageLock = Any()
    }
}
