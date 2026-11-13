package ai.moa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionEngineTest {
    private fun started() = SessionEngine().apply {
        updateSafety(Safety.ALLOWED)
        assertTrue(start())
    }

    @Test fun `unknown or restricted status never starts a session`() {
        val engine = SessionEngine()
        assertFalse(engine.start())
        engine.updateSafety(Safety.BLOCKED)
        assertFalse(engine.start())
        engine.updateSafety(Safety.ALLOWED)
        assertEquals(Stage.HOME, engine.state.stage)
        assertTrue(engine.start())
    }

    @Test fun `all ten topics and all four paths yield actual choices and one family question`() {
        assertEquals(10, QuestionCatalog.cards.size)
        assertEquals(10, QuestionCatalog.cards.map { it.id }.toSet().size)
        QuestionCatalog.cards.forEach { card ->
            listOf("sea", "mountain").forEach { destination ->
                listOf("greeting", "rest").forEach { activity ->
                    val engine = started()
                    assertTrue(engine.selectCard(card.id))
                    assertEquals(card.fact, engine.state.card?.fact)
                    assertTrue(engine.startStory())
                    assertTrue(engine.state.storyText.contains("상상 이야기"))
                    assertTrue(engine.state.storyText.contains(card.subject))
                    assertTrue(engine.choose(destination))
                    assertTrue(engine.choose(activity))
                    assertEquals(Stage.ENDING, engine.state.stage)
                    assertFalse(engine.choose(activity))
                    assertTrue(engine.finishStory())
                    val family = assertNotNull(engine.state.familyCard)
                    assertEquals(engine.state.choices.map { it.label }, family.choices)
                    assertEquals(card.question, family.question)
                    assertEquals(card.familyQuestion, family.openQuestion)
                    assertFalse(family.isHandoff)
                }
            }
        }
    }

    @Test fun `facts only match complete supported questions never incidental topic keywords`() {
        assertEquals("cloud", QuestionCatalog.matchQuestion("구름은 왜 움직여?")?.id)
        assertEquals("cloud", QuestionCatalog.matchQuestion("  구름은 왜 움직여요?!  ")?.id)
        assertNull(QuestionCatalog.matchQuestion("구름은 무게가 얼마야?"))
        assertNull(QuestionCatalog.matchQuestion("구름은 왜 안 움직여?"))
        assertNull(QuestionCatalog.matchQuestion("구름"))
        assertNull(QuestionCatalog.matchQuestion("달에 사람이 살아요?"))
        assertNull(QuestionCatalog.matchQuestion("저 산 이름이 뭐야?"))
        assertNull(QuestionCatalog.matchQuestion(""))
    }

    @Test fun `unknown questions get at most two clarifications then an honest handoff`() {
        val engine = started()
        repeat(2) { attempt ->
            assertTrue(engine.submitQuestion("구름은 무게가 얼마야?"))
            assertEquals(Stage.QUESTION, engine.state.stage)
            assertEquals(attempt + 1, engine.state.clarificationCount)
            assertNull(engine.state.card)
        }
        assertTrue(engine.submitQuestion("구름은 무게가 얼마야?"))
        assertEquals(Stage.FAMILY, engine.state.stage)
        assertEquals(2, engine.state.clarificationCount)
        val family = assertNotNull(engine.state.familyCard)
        assertTrue(family.isHandoff)
        assertEquals("구름은 무게가 얼마야?", family.question)
        assertTrue(family.choices.isEmpty())
        assertFalse(engine.startStory())
    }

    @Test fun `clarification can resolve by explicit card selection or supported question`() {
        val engine = started()
        engine.submitQuestion("구름")
        assertEquals("cloud", engine.state.suggestedCards.first().id)
        assertTrue(engine.selectCard("cloud"))
        assertEquals(Stage.FACT, engine.state.stage)
        assertEquals("구름은 왜 움직여?", engine.state.question)
        assertTrue(engine.backToQuestions())
        engine.submitQuestion("비")
        assertTrue(engine.submitQuestion("비는 왜 내려요?"))
        assertEquals("rain", engine.state.card?.id)
    }

    @Test fun `empty questions do not consume clarification and inputs are bounded`() {
        val engine = started()
        assertFalse(engine.submitQuestion("  "))
        assertEquals(0, engine.state.clarificationCount)
        engine.submitQuestion("가".repeat(5000))
        assertEquals(SessionEngine.MAX_QUESTION_LENGTH, engine.state.question.length)
        engine.submitQuestion("구름은 왜 움직여?" + " ".repeat(200) + "그리고 무게는 얼마야?")
        assertEquals(Stage.QUESTION, engine.state.stage)
        assertNull(engine.state.card)
    }

    @Test fun `every active stage pauses on restriction and requires explicit allowed resume`() {
        val builders: List<(SessionEngine) -> Unit> = listOf(
            {},
            { it.selectCard("cloud") },
            { it.selectCard("cloud"); it.startStory() },
            { it.selectCard("cloud"); it.startStory(); it.choose("sea"); it.choose("rest") },
            { it.selectCard("cloud"); it.startStory(); it.choose("sea"); it.choose("rest"); it.finishStory() },
        )
        builders.forEach { prepare ->
            val engine = started()
            prepare(engine)
            val previous = engine.state
            engine.updateSafety(Safety.BLOCKED)
            assertEquals(Stage.PAUSED, engine.state.stage)
            assertEquals(previous.stage, engine.state.pausedStage)
            assertFalse(engine.resume())
            assertFalse(engine.choose("sea"))
            assertFalse(engine.selectCard("cloud"))
            assertFalse(engine.submitQuestion("비는 왜 내려?"))
            assertFalse(engine.setTheme(Theme.SPACE))
            engine.updateSafety(Safety.UNKNOWN)
            assertFalse(engine.resume())
            engine.updateSafety(Safety.ALLOWED)
            assertEquals(Stage.PAUSED, engine.state.stage)
            assertTrue(engine.resume())
            assertEquals(previous.stage, engine.state.stage)
            assertEquals(previous.choices, engine.state.choices)
        }
    }

    @Test fun `manual pause and repeated callbacks preserve original stage`() {
        val engine = started()
        engine.selectCard("moon")
        engine.startStory()
        engine.choose("mountain")
        engine.pause("앱이 백그라운드로 이동했어요.")
        engine.pause("권한을 확인해주세요.")
        engine.updateSafety(Safety.UNKNOWN)
        engine.updateSafety(Safety.ALLOWED)
        assertTrue(engine.resume())
        assertEquals(Stage.STORY, engine.state.stage)
        assertEquals("mountain", engine.state.choices.single().id)
    }

    @Test fun `clarification prompt and question survive repeated interruptions unchanged`() {
        val engine = started()
        engine.submitQuestion("구름의 무게는 얼마야?")
        val beforePause = engine.state
        engine.pause()
        engine.updateSafety(Safety.UNKNOWN)
        engine.pause("화면을 벗어났어요.")
        engine.updateSafety(Safety.ALLOWED)
        assertTrue(engine.resume())
        assertEquals(beforePause, engine.state)
        engine.submitQuestion("구름의 무게는 얼마야?")
        assertEquals(2, engine.state.clarificationCount)
    }

    @Test fun `handoff uses the latest draft and rejects blank or paused edits`() {
        val engine = started()
        engine.submitQuestion("구름의 무게는 얼마야?")
        val beforeBlank = engine.state
        assertFalse(engine.handOffQuestion("  "))
        assertEquals(beforeBlank, engine.state)
        engine.pause()
        assertFalse(engine.handOffQuestion("새 질문"))
        engine.resume()
        assertTrue(engine.handOffQuestion("  공룡은 왜 사라졌어?  "))
        assertEquals("공룡은 왜 사라졌어?", engine.state.question)
        assertEquals(engine.state.question, engine.state.familyCard?.question)
        assertTrue(engine.state.familyCard!!.choices.isEmpty())
    }

    @Test fun `cannot bypass story choices or inject an arbitrary choice`() {
        val engine = started()
        assertFalse(engine.startStory())
        assertFalse(engine.selectCard("camera-detected-mountain"))
        engine.selectCard("cloud")
        assertFalse(engine.choose("sea"))
        engine.startStory()
        assertFalse(engine.finishStory())
        assertFalse(engine.choose("rest"))
        engine.choose("sea")
        assertFalse(engine.choose("mountain"))
        assertFalse(engine.finishStory())
        engine.choose("greeting")
        assertTrue(engine.finishStory())
        assertFalse(engine.finishStory())
    }

    @Test fun `end clears every personal session field even while paused`() {
        val engine = started()
        engine.submitQuestion("우리 가족 비밀 질문")
        engine.handOffQuestion()
        engine.updateSafety(Safety.BLOCKED)
        engine.end()
        assertEquals(SessionState(safety = Safety.BLOCKED), engine.state)
        engine.updateSafety(Safety.ALLOWED)
        assertFalse(engine.resume())
        assertTrue(engine.start())
        assertNull(engine.state.familyCard)
        assertEquals("", engine.state.question)
    }

    @Test fun `theme palette is bounded and never affects factual content`() {
        val engine = started()
        engine.selectCard("star")
        val fact = engine.state.card?.fact
        Theme.entries.forEach { theme ->
            assertEquals(3, theme.colors.size)
            assertTrue(engine.setTheme(theme))
            assertEquals(fact, engine.state.card?.fact)
        }
    }
}
