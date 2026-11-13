package ai.moa.app.storage

import ai.moa.core.FamilyCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FamilyCardStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val file get() = File(context.filesDir, FamilyCardStore.FILE_NAME)
    private lateinit var store: FamilyCardStore
    private val sample = FamilyCard(
        question = "구름은 왜 움직여?",
        subject = "구름",
        choices = listOf("바다로 갈래", "새 친구에게 인사할래"),
        openQuestion = "구름을 타고 어디에 가보고 싶어?",
    )

    @Before fun prepare() {
        store = FamilyCardStore(context)
        store.clear()
    }

    @Test fun `nothing is saved until an explicit save and all fields survive a fresh instance`() {
        assertTrue(store.all().isEmpty())
        assertFalse(file.exists())
        val saved = store.save(sample)
        assertTrue(saved.createdAt > 0)
        val reloaded = FamilyCardStore(context).all().single()
        assertEquals(saved.id, reloaded.id)
        assertEquals(saved.createdAt, reloaded.createdAt)
        assertEquals(sample, reloaded.card)
        assertEquals(sample.choices, reloaded.card.choices)
    }

    @Test fun `new cards are first and deletion preserves other cards across instances`() {
        val first = store.save(sample)
        val second = store.save(sample.copy(question = "달은 왜 빛나?", subject = "달"))
        assertEquals(listOf(second.id, first.id), store.all().map { it.id })
        FamilyCardStore(context).delete(first.id)
        assertEquals(listOf(second.id), store.all().map { it.id })
        store.delete("unknown-id")
        assertEquals(listOf(second.id), store.all().map { it.id })
        store.clear()
        assertTrue(FamilyCardStore(context).all().isEmpty())
        assertFalse(file.exists())
    }

    @Test fun `handoff cards round trip without inventing story selections`() {
        val card = FamilyCard("공룡은 왜 사라졌어?", "함께 알아볼 질문", emptyList(), "어디서 함께 알아볼까?", true)
        store.save(card)
        assertEquals(card, FamilyCardStore(context).all().single().card)
    }

    @Test fun `unreadable documents reject reads and mutations without overwriting original data`() {
        val oversizedEntries = JSONArray().apply { repeat(101) { put(JSONObject()) } }
        listOf(
            "{broken json",
            "{\"version\":999,\"cards\":[]}",
            "{\"version\":1.5,\"cards\":[]}",
            "{\"version\":1}",
            " ".repeat(512_001),
            JSONObject().put("version", 1).put("cards", oversizedEntries).toString(),
        ).forEach { original ->
            file.writeText(original)
            assertThrows(IOException::class.java) { store.all() }
            assertThrows(IOException::class.java) { store.save(sample) }
            assertThrows(IOException::class.java) { store.delete("unknown-id") }
            assertEquals(original, file.readText())
        }
        // Explicitly clearing is the only operation allowed to discard unreadable data.
        store.clear()
        assertTrue(store.all().isEmpty())
        val saved = store.save(sample)
        assertEquals(saved, store.all().single())
    }

    @Test fun `invalid individual records do not hide valid saved cards`() {
        val saved = store.save(sample)
        val document = JSONObject(file.readText())
        document.getJSONArray("cards").put(JSONObject().put("id", "wrong").put("question", "broken"))
        file.writeText(document.toString())
        assertEquals(listOf(saved), store.all())
        val partiallyReadable = file.readText()
        assertThrows(IOException::class.java) { store.save(sample) }
        assertThrows(IOException::class.java) { store.delete(saved.id) }
        assertEquals(partiallyReadable, file.readText())
        val validEntry = document.getJSONArray("cards").getJSONObject(0)
        validEntry.put("choices", JSONArray().put("one").put("two").put("three"))
        file.writeText(document.toString())
        assertThrows(IOException::class.java) { store.all() }
    }

    @Test fun `an unreadable storage path is reported without deleting its contents`() {
        assertTrue(file.mkdir())
        val marker = File(file, "existing-data")
        marker.writeText("keep me")
        try {
            assertThrows(IOException::class.java) { store.all() }
            assertThrows(IOException::class.java) { store.save(sample) }
            assertThrows(IOException::class.java) { store.delete("unknown-id") }
            assertEquals("keep me", marker.readText())
        } finally {
            assertTrue(marker.delete())
            assertTrue(file.delete())
        }
    }

    @Test fun `a backup is recovered and an orphan pending write is not treated as empty`() {
        val saved = store.save(sample)
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        assertEquals(listOf(saved), store.all())
        assertFalse(backup.exists())

        store.clear()
        val pending = File(file.path + ".new")
        pending.writeText("unfinished save")
        assertThrows(IOException::class.java) { store.all() }
        assertThrows(IOException::class.java) { store.save(sample) }
        assertEquals("unfinished save", pending.readText())
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test fun `new saves cap retention at 100 and remove the oldest entry`() {
        val oldest = store.save(sample)
        repeat(FamilyCardStore.MAX_CARDS) { index ->
            store.save(sample.copy(question = "질문 $index"))
        }
        val cards = FamilyCardStore(context).all()
        assertEquals(FamilyCardStore.MAX_CARDS, cards.size)
        assertFalse(cards.any { it.id == oldest.id })
        assertEquals("질문 99", cards.first().card.question)
    }

    @Test fun `oversized invalid cards fail without destroying saved data`() {
        val saved = store.save(sample)
        assertThrows(IllegalArgumentException::class.java) {
            store.save(sample.copy(question = "가".repeat(201)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save(sample.copy(choices = listOf("하나", "둘", "셋")))
        }
        assertEquals(listOf(saved), store.all())
    }

    @Test fun `write failures are reported and previous data remains readable`() {
        val saved = store.save(sample)
        val blocked = File(file.path + ".new")
        assertTrue(blocked.mkdir())
        val marker = File(blocked, "blocks-atomic-write")
        marker.writeText("test")
        assertThrows(IOException::class.java) { store.save(sample.copy(subject = "달")) }
        assertTrue(marker.delete())
        assertTrue(blocked.delete())
        assertEquals(listOf(saved), store.all())
    }
}
