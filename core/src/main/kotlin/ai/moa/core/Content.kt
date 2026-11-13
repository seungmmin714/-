package ai.moa.core

import java.text.Normalizer
import java.util.Locale

/** Locally authored, fixed explanations. Free text never generates a factual answer. */
data class QuestionCard(
    val id: String,
    val subject: String,
    val question: String,
    val fact: String,
    val familyQuestion: String,
    val aliases: List<String>,
)

enum class Theme(val label: String, val colors: List<Long>) {
    SEA("바다", listOf(0xFF69CFF6, 0xFF3289DB, 0xFFE3F6FF)),
    FOREST("숲", listOf(0xFF8BC7A1, 0xFF3A8A6B, 0xFFEAF5DD)),
    SPACE("별빛", listOf(0xFF8F94DE, 0xFF5B62AB, 0xFFE9E6FF)),
}

object QuestionCatalog {
    val cards: List<QuestionCard> = listOf(
        QuestionCard(
            "cloud", "구름", "구름은 왜 움직여?",
            "구름은 아주 작은 물방울이나 얼음 알갱이가 모인 거야. 바람이 불면 공기와 함께 구름도 움직여.",
            "구름을 타고 어디에 가보고 싶어?",
            listOf("구름은 왜 움직일까", "구름이 왜 움직여", "왜 구름이 움직여", "구름은 어떻게 움직여", "구름은 왜 움직이나요"),
        ),
        QuestionCard(
            "rain", "비", "비는 왜 내려?",
            "구름 속 작은 물방울들이 서로 만나 점점 커져. 공기 중에 떠 있기 어려울 만큼 무거워지면 땅으로 떨어져. 그게 비야.",
            "비 오는 날에 가족과 무엇을 해보고 싶어?",
            listOf("비는 왜 내릴까", "비가 왜 내려", "왜 비가 내려", "비는 어떻게 내려", "비는 왜 내리나요"),
        ),
        QuestionCard(
            "rainbow", "무지개", "무지개는 어떻게 생겨?",
            "햇빛에는 여러 색의 빛이 섞여 있어. 햇빛이 공기 중 물방울을 지나며 꺾이고 물방울 안에서 반사되면, 여러 색으로 나뉜 무지개를 볼 수 있어.",
            "우리 가족만의 무지개를 만든다면 어떤 색을 넣고 싶어?",
            listOf("무지개는 왜 생겨", "무지개가 왜 생겨", "무지개는 어떻게 생길까", "무지개는 어떻게 생기나요"),
        ),
        QuestionCard(
            "mountain", "산", "산은 어떻게 생겨?",
            "땅은 아주 오랜 시간에 걸쳐 움직여. 땅이 서로 밀려 솟아오르거나, 화산에서 나온 물질이 쌓여 산이 되기도 해.",
            "상상 속 산꼭대기에 무엇이 있으면 좋겠어?",
            listOf("산은 왜 생겨", "산이 어떻게 생겨", "산은 어떻게 만들어져", "산은 어떻게 생기나요"),
        ),
        QuestionCard(
            "river", "강", "강물은 왜 흘러?",
            "지구가 물을 아래쪽으로 끌어당기기 때문이야. 그래서 강물은 보통 높은 곳에서 낮은 곳으로 흐르며 길을 이어 가.",
            "강에 종이배를 띄운다면 어떤 이름을 붙이고 싶어?",
            listOf("강은 왜 흘러", "강물이 왜 흘러", "강물은 왜 흐를까", "강물은 왜 흐르나요"),
        ),
        QuestionCard(
            "sea", "바다", "바닷물은 왜 짜?",
            "물은 땅과 바위를 지나며 소금 성분을 조금씩 녹여 바다로 가져와. 바닷물이 증발해도 소금 성분은 남아서, 바다는 짠맛이 나.",
            "바닷가에서 가족과 어떤 보물을 찾아보고 싶어?",
            listOf("바다는 왜 짜", "바닷물이 왜 짜", "바닷물은 왜 짤까", "바닷물은 왜 짠가요"),
        ),
        QuestionCard(
            "tree", "나무", "나무는 어떻게 자라?",
            "나무는 뿌리로 물을 빨아들이고 잎으로 햇빛을 받아. 햇빛을 이용해 물과 공기 속 이산화탄소로 양분을 만들며 자라.",
            "우리 가족의 상상 나무에는 무엇이 열리면 좋겠어?",
            listOf("나무가 어떻게 자라", "나무는 왜 자라", "나무는 어떻게 자랄까", "나무는 어떻게 자라나요"),
        ),
        QuestionCard(
            "wind", "바람", "바람은 왜 불어?",
            "햇빛이 땅과 공기를 고르게 데우지는 않아. 그러면 곳곳의 기압이 달라지고, 공기가 이동해. 이렇게 움직이는 공기가 바람이야.",
            "바람에게 가족의 말을 하나 전한다면 무엇을 말하고 싶어?",
            listOf("바람이 왜 불어", "바람은 어떻게 생겨", "바람은 왜 불까", "바람은 왜 부나요"),
        ),
        QuestionCard(
            "moon", "달", "달은 왜 빛나?",
            "달은 스스로 빛을 만들지 않아. 태양의 빛을 받아 반사해서 밝게 보이는 거야.",
            "달에 편지를 보낸다면 어떤 이야기를 적고 싶어?",
            listOf("달이 왜 빛나", "달은 어떻게 빛나", "달은 왜 빛날까", "달은 왜 빛나나요"),
        ),
        QuestionCard(
            "star", "별", "별은 왜 반짝여?",
            "별은 스스로 빛을 내. 그 빛이 움직이는 지구의 공기층을 지나면서 방향이 조금씩 바뀌어, 우리 눈에는 반짝이는 것처럼 보여.",
            "가족만의 별자리를 만든다면 어떤 모양으로 만들고 싶어?",
            listOf("별이 왜 반짝여", "별은 왜 깜빡여", "별은 왜 반짝일까", "별은 왜 반짝이나요"),
        ),
    )

    fun byId(id: String): QuestionCard? = cards.firstOrNull { it.id == id }

    /** Full-question allowlist: a subject keyword alone must never select an unrelated answer. */
    fun matchQuestion(input: String): QuestionCard? {
        val normalized = normalize(input)
        if (normalized.isBlank()) return null
        return cards.firstOrNull { card ->
            (card.aliases + card.question).any { normalize(it) == normalized }
        }
    }

    fun suggestions(input: String): List<QuestionCard> {
        val normalized = normalize(input)
        if (normalized.isBlank()) return emptyList()
        return cards.filter { normalized.contains(normalize(it.subject)) }.take(3)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}？！。，]+"), "")
            .removeSuffix("요")
}

data class StoryChoice(val id: String, val label: String)

data class FamilyCard(
    val question: String,
    val subject: String,
    val choices: List<String>,
    val openQuestion: String,
    val isHandoff: Boolean = false,
)

internal object StoryTemplate {
    val destinations = listOf(
        StoryChoice("sea", "바다로 갈래"),
        StoryChoice("mountain", "산으로 갈래"),
    )
    val activities = listOf(
        StoryChoice("greeting", "새 친구에게 인사할래"),
        StoryChoice("rest", "풍경을 보며 쉬어 갈래"),
    )

    fun choices(selected: List<StoryChoice>): List<StoryChoice> = when (selected.size) {
        0 -> destinations
        1 -> activities
        else -> emptyList()
    }

    fun text(subject: String, selected: List<StoryChoice>): String {
        val companion = "‘$subject’ 친구"
        if (selected.isEmpty()) {
            return "이제 상상 이야기야. ${companion}가 작은 여행 가방을 들었어. “우리 어디로 가볼까?” 두 갈래 길 앞에서 네 선택을 기다려."
        }
        val sea = selected.first().id == "sea"
        val place = if (sea) "반짝이는 바다" else "초록빛 산"
        val friend = if (sea) "작은 조개" else "작은 솔방울"
        if (selected.size == 1) {
            return "${companion}와 ${place}에 도착했어. 길가의 $friend 친구가 손을 흔들어. 여기서 무엇을 해볼까?"
        }
        val moment = if (selected[1].id == "greeting") {
            "“안녕! 우리는 여행 중이야.” 서로 인사를 나누자 $friend 친구가 웃으며 길을 알려 주었어."
        } else {
            "잠깐 앉아서 $place 풍경을 함께 바라봤어. $friend 친구도 옆에서 조용히 쉬었어."
        }
        return "$moment ${companion}는 오늘의 여행을 작은 가방에 담았어. “함께 와 줘서 고마워.” 이제 우리 가족의 이야기로 돌아갈 시간이야."
    }
}
