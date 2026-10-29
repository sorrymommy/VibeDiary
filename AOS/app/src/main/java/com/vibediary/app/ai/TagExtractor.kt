package com.vibediary.app.ai

/**
 * Fully on-device, rule-based tag extraction for Korean diary text.
 *
 * Sources, in priority order:
 *  1. explicit `#해시태그` the user typed or said
 *  2. topic/mood categories matched by keyword stems
 *  3. photo labels and the place tag
 *  4. content words that repeat (after stripping Korean particles)
 *
 * Swappable later for an on-device LLM (e.g. Gemini Nano) behind the same signature.
 */
object TagExtractor {
    private const val MAX_TAGS = 8

    fun extract(text: String, imageTags: List<String> = emptyList(), placeTag: String? = null): List<String> {
        val tags = LinkedHashSet<String>()

        HASHTAG.findAll(text).forEach { tags += it.groupValues[1] }

        val normalized = text.lowercase()
        CATEGORIES.forEach { (tag, stems) -> if (stems.any { normalized.contains(it) }) tags += tag }

        tags += imageTags
        placeTag?.let { tags += it }

        WORD.findAll(text.replace(HASHTAG, " "))
            .map { stripParticle(it.value) }
            .filter { it.length >= 2 && it !in STOPWORDS && !looksLikePredicate(it) }
            .groupingBy { it }.eachCount()
            .filter { it.value >= 2 }
            .entries.sortedByDescending { it.value }
            .take(3)
            .forEach { tags += it.key }

        return tags.filter { it.isNotBlank() }.take(MAX_TAGS)
    }

    private val HASHTAG = Regex("#([\\p{L}\\p{N}_]+)")
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    private val PARTICLES = listOf(
        "에서는", "으로는", "에게서", "한테서", "이라고", "에서", "에게", "한테", "으로", "까지", "부터",
        "처럼", "보다", "하고", "이랑", "이나", "이는", "은", "는", "이", "가", "을", "를", "에", "의",
        "도", "로", "와", "과", "랑", "만",
    )

    private fun stripParticle(word: String): String {
        for (p in PARTICLES) {
            if (word.length > p.length + 1 && word.endsWith(p)) return word.dropLast(p.length)
        }
        return word
    }

    private val PREDICATE_ENDINGS = listOf(
        "다", "요", "죠", "네", "까", "고", "서", "면", "지", "게", "던", "는데", "어서", "아서", "했", "했던", "하는",
    )

    private fun looksLikePredicate(word: String) = PREDICATE_ENDINGS.any { word.endsWith(it) }

    private val STOPWORDS = setOf(
        "오늘", "어제", "내일", "지금", "이제", "아까", "방금", "그리고", "그래서", "그런데", "하지만", "근데",
        "정말", "진짜", "너무", "완전", "조금", "약간", "그냥", "많이", "다시", "계속", "우리", "나는", "내가",
        "저는", "제가", "이거", "그거", "저거", "이것", "그것", "여기", "거기", "뭔가", "생각", "느낌", "사람",
    )

    private val CATEGORIES: Map<String, List<String>> = mapOf(
        "음식" to listOf("먹었", "먹고", "먹는", "밥", "점심", "저녁", "아침", "맛집", "식당", "요리", "라면", "고기", "치킨", "피자", "회식"),
        "카페" to listOf("카페", "커피", "라떼", "아메리카노", "디저트", "케이크", "빵집"),
        "운동" to listOf("운동", "헬스", "러닝", "달리기", "뛰었", "산책", "요가", "필라테스", "수영", "자전거", "등산", "걸었"),
        "여행" to listOf("여행", "비행기", "공항", "호텔", "숙소", "캠핑", "휴가"),
        "일" to listOf("회의", "업무", "출근", "퇴근", "프로젝트", "회사", "미팅", "야근", "보고서", "마감"),
        "공부" to listOf("공부", "강의", "독서", "시험", "학교", "수업", "읽었"),
        "가족" to listOf("엄마", "아빠", "가족", "부모님", "동생", "할머니", "할아버지", "남편", "아내", "아들", "딸"),
        "친구" to listOf("친구", "모임", "약속", "동창"),
        "아이디어" to listOf("아이디어", "떠올", "생각났", "기획", "만들어보", "해보면", "아이템"),
        "감사" to listOf("감사", "고마", "고맙"),
        "행복" to listOf("행복", "좋았", "즐거", "신났", "신나", "설레", "기뻤", "기쁘", "뿌듯"),
        "피곤" to listOf("피곤", "지쳤", "지친", "힘들", "졸려", "졸리"),
        "우울" to listOf("슬프", "슬펐", "우울", "눈물", "외로", "속상"),
        "날씨" to listOf("비가", "비 와", "눈이", "날씨", "맑", "흐리", "더워", "덥다", "추워", "춥다"),
        "쇼핑" to listOf("쇼핑", "샀다", "샀어", "구매", "주문했", "택배"),
        "문화" to listOf("영화", "드라마", "넷플릭스", "공연", "전시", "뮤지컬", "미술관"),
        "음악" to listOf("노래", "음악", "콘서트", "플레이리스트"),
        "건강" to listOf("병원", "약국", "아팠", "아프", "감기", "두통"),
    )
}
