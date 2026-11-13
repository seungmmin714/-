package ai.moa.core

enum class Stage { HOME, QUESTION, FACT, STORY, ENDING, FAMILY, PAUSED }
enum class Safety { ALLOWED, BLOCKED, UNKNOWN }

data class SessionState(
    val stage: Stage = Stage.HOME,
    val safety: Safety = Safety.UNKNOWN,
    val card: QuestionCard? = null,
    val question: String = "",
    val clarificationCount: Int = 0,
    val message: String = "",
    val theme: Theme = Theme.SEA,
    val choices: List<StoryChoice> = emptyList(),
    val familyCard: FamilyCard? = null,
    val pausedStage: Stage? = null,
) {
    val storyText: String
        get() = card?.let { StoryTemplate.text(it.subject, choices) }.orEmpty()
    val availableChoices: List<StoryChoice>
        get() = if (stage == Stage.STORY) StoryTemplate.choices(choices) else emptyList()
    val suggestedCards: List<QuestionCard>
        get() = if (stage == Stage.QUESTION) QuestionCatalog.suggestions(question) else emptyList()
}

/**
 * In-memory, synchronous state machine. All user transitions fail closed unless the
 * platform permits interaction. Becoming allowed never starts or resumes a session.
 * Persistence and platform effects belong to adapters, never to this engine.
 */
class SessionEngine {
    var state: SessionState = SessionState()
        private set

    fun updateSafety(safety: Safety) {
        state = state.copy(safety = safety)
        if (safety != Safety.ALLOWED && state.stage != Stage.HOME) {
            pause(if (safety == Safety.BLOCKED) "주행 제한으로 잠시 멈췄어요." else "이용 가능한 차량 상태를 확인할 수 없어 멈췄어요.")
        }
    }

    fun start(): Boolean {
        if (!allowed(Stage.HOME)) return false
        state = SessionState(stage = Stage.QUESTION, safety = state.safety, message = "오는 길에 무엇이 궁금했어?")
        return true
    }

    fun selectCard(id: String): Boolean {
        if (!allowed(Stage.QUESTION, Stage.FACT)) return false
        val card = QuestionCatalog.byId(id) ?: return false
        state = state.copy(
            stage = Stage.FACT, card = card, question = card.question,
            clarificationCount = 0, message = "사실 설명", choices = emptyList(), familyCard = null,
        )
        return true
    }

    fun submitQuestion(text: String): Boolean {
        if (!allowed(Stage.QUESTION)) return false
        val original = text.trim()
        val question = original.take(MAX_QUESTION_LENGTH)
        if (question.isBlank()) {
            state = state.copy(message = "궁금한 것을 말하거나 질문 카드를 골라 줘.")
            return false
        }
        // Do not turn a truncated long question into an apparently supported FAQ.
        val match = if (original.length <= MAX_QUESTION_LENGTH) QuestionCatalog.matchQuestion(original) else null
        if (match != null) {
            state = state.copy(
                stage = Stage.FACT, card = match, question = question,
                clarificationCount = 0, message = "사실 설명", choices = emptyList(), familyCard = null,
            )
            return true
        }
        state = state.copy(question = question)
        if (state.clarificationCount >= MAX_CLARIFICATIONS) {
            return handOffQuestion()
        }
        val suggested = QuestionCatalog.suggestions(question).firstOrNull()
        val prompt = suggested?.let { "‘${it.question}’가 궁금한 거야? 맞으면 아래 질문 카드를 골라 줘. 다른 질문이면 다시 말해도 좋아." }
            ?: "어떤 것이 궁금한지 한 번 더 알려 줄래? 아래 질문 카드를 골라도 좋아."
        state = state.copy(clarificationCount = state.clarificationCount + 1, message = prompt)
        return true
    }

    fun handOffQuestion(): Boolean {
        if (!allowed(Stage.QUESTION) || state.question.isBlank()) return false
        state = state.copy(
            stage = Stage.FAMILY,
            message = "아직 모아의 설명 카드에 없는 질문이야. 가족과 함께 알아보자.",
            familyCard = FamilyCard(
                question = state.question, subject = "함께 알아볼 질문", choices = emptyList(),
                openQuestion = "이 질문을 가족과 어디에서 함께 알아볼까?", isHandoff = true,
            ),
        )
        return true
    }

    fun backToQuestions(): Boolean {
        if (!allowed(Stage.FACT)) return false
        state = SessionState(stage = Stage.QUESTION, safety = state.safety, theme = state.theme)
        return true
    }

    fun startStory(): Boolean {
        if (!allowed(Stage.FACT) || state.card == null) return false
        state = state.copy(stage = Stage.STORY, choices = emptyList(), message = "이제 상상 이야기를 만들어볼까?")
        return true
    }

    fun choose(id: String): Boolean {
        if (!allowed(Stage.STORY)) return false
        val choice = state.availableChoices.firstOrNull { it.id == id } ?: return false
        val selected = state.choices + choice
        val theme = when (choice.id) {
            "sea" -> Theme.SEA
            "mountain" -> Theme.FOREST
            else -> state.theme
        }
        state = state.copy(
            choices = selected,
            theme = theme,
            stage = if (selected.size == MAX_CHOICES) Stage.ENDING else Stage.STORY,
        )
        return true
    }

    fun showFamilyCard(): Boolean {
        if (!allowed(Stage.ENDING) || state.choices.size != MAX_CHOICES) return false
        val card = state.card ?: return false
        state = state.copy(
            stage = Stage.FAMILY, message = "오늘의 호기심을 가족과 나눠요.",
            familyCard = FamilyCard(
                question = state.question, subject = card.subject,
                choices = state.choices.map { it.label }, openQuestion = card.familyQuestion,
            ),
        )
        return true
    }

    fun finishStory(): Boolean = showFamilyCard()

    fun setTheme(theme: Theme): Boolean {
        if (!allowed(Stage.QUESTION, Stage.FACT, Stage.STORY, Stage.ENDING, Stage.FAMILY)) return false
        state = state.copy(theme = theme)
        return true
    }

    fun pause(reason: String = "보호자가 잠시 멈췄어요.") {
        if (state.stage == Stage.HOME) return
        state = state.copy(
            stage = Stage.PAUSED,
            pausedStage = state.pausedStage ?: state.stage,
            message = reason,
        )
    }

    fun resume(): Boolean {
        if (!allowed(Stage.PAUSED)) return false
        val previous = state.pausedStage ?: return false
        state = state.copy(stage = previous, pausedStage = null, message = "보호자가 다시 시작했어요.")
        return true
    }

    /** Ending is always allowed, even when vehicle restrictions are unknown. */
    fun end() {
        state = SessionState(safety = state.safety)
    }

    private fun allowed(vararg stages: Stage): Boolean =
        state.safety == Safety.ALLOWED && state.stage in stages

    companion object {
        const val MAX_CLARIFICATIONS = 2
        const val MAX_CHOICES = 2
        const val MAX_QUESTION_LENGTH = 200
    }
}
