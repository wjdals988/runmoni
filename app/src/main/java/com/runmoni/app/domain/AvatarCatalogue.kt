package com.runmoni.app.domain

data class AvatarSlot(val key: String, val label: String, val variants: List<String>)

/** Stable saved item IDs; all hair/clothes work on the same face/body. */
object AvatarCatalogue {
    val slots = listOf(
        AvatarSlot("hair", "헤어", listOf("텍스처 쇼트", "가르마 쇼트", "포니테일", "단발")),
        AvatarSlot("headwear", "모자", listOf("모자 없음", "러닝 캡", "선바이저", "스웨트 헤드밴드")),
        AvatarSlot("face", "얼굴", listOf("편안한 표정", "활짝 웃음", "집중하는 표정", "윙크")),
        AvatarSlot("top", "상의", listOf("코랄 싱글렛", "민트 러닝 반팔", "블루 긴팔", "옐로 바람막이", "스포츠 브라탑", "크롭 기능성 티")),
        AvatarSlot("bottom", "하의", listOf("네이비 쇼츠", "러닝 타이츠", "그린 쇼츠", "코랄 쇼츠", "하이웨이스트 바이커쇼츠", "스플릿 레이싱 쇼츠")),
        AvatarSlot("costume", "세트", listOf("일반 러닝복", "산타 러너", "스포츠 원피스 수영복", "스포츠 비키니", "곰인형 코스튬", "골든 마스코트")),
        AvatarSlot("socks", "양말", listOf("발목 양말", "크루 양말", "니삭스", "양말 없음")),
        AvatarSlot("shoes", "신발", listOf("민트 러닝화", "코랄 러닝화", "블루 러닝화", "골드 러닝화")),
        AvatarSlot("ear", "이어폰", listOf("귀 장비 없음", "오픈형 이어폰", "스포츠 이어버드", "스포츠 헤드셋")),
        AvatarSlot("eyewear", "선글라스", listOf("선글라스 없음", "민트 스포츠 선글라스", "코랄 스포츠 선글라스", "블루 스포츠 선글라스")),
        AvatarSlot("wrist", "워치", listOf("워치 없음", "민트 스포츠 워치", "코랄 스포츠 워치", "블루 스포츠 워치")),
        AvatarSlot("waist", "러닝 벨트", listOf("벨트 없음", "민트 러닝 벨트", "코랄 러닝 벨트", "블루 러닝 벨트")),
        AvatarSlot("hydration", "수분 조끼", listOf("조끼 없음", "민트 수분 보급 조끼", "코랄 수분 보급 조끼", "블루 수분 보급 조끼")),
    )
    val accessoryKeys = setOf("ear", "eyewear", "wrist", "waist", "hydration")
    val tabs = listOf("hair" to "헤어", "headwear" to "모자", "face" to "얼굴", "top" to "상의", "bottom" to "하의",
        "costume" to "세트", "socks" to "양말", "shoes" to "신발", "accessories" to "액세서리")
    val defaultOwned: Set<String> = slots.map { "${it.key}:0" }.toSet() + "accessory:0"
    fun valid(slot: String, variant: Int) = if (slot == "accessory") variant in 0..3 else slots.firstOrNull { it.key == slot }?.let { variant in it.variants.indices } == true
    fun price(variant: Int): Int { require(variant in 0..5); return listOf(0, 500, 1000, 2000, 500, 1000)[variant] }
    fun migrateOwned(owned: Set<String>): Set<String> = owned + defaultOwned + buildSet {
        if ("accessory:1" in owned) add("headwear:3")
        if ("accessory:2" in owned) add("headwear:1")
        if ("accessory:3" in owned) add("ear:1")
    }
    fun coveredSlots(costume: Int): Set<String> = when (costume) {
        1, 4, 5 -> setOf("headwear", "top", "bottom", "socks", "shoes")
        2, 3 -> setOf("top", "bottom")
        else -> emptySet()
    }
}

fun AvatarStyle.variantFor(slot: String) = when (slot) {
    "hair" -> hair; "top" -> top; "bottom" -> bottom; "shoes" -> shoes
    "accessory" -> accessory; "eyewear" -> eyewear; "wrist" -> wrist
    "waist" -> waist; "hydration" -> hydration; "face" -> face; "socks" -> socks
    "headwear" -> headwear ?: when (accessory) { 1 -> 3; 2 -> 1; else -> 0 }
    "ear" -> ear ?: if (accessory == 3) 1 else 0
    "costume" -> costume; else -> error("잘못된 아이템 슬롯")
}

fun AvatarStyle.withItem(slot: String, variant: Int): AvatarStyle {
    require(AvatarCatalogue.valid(slot, variant)) { "올바른 아이템을 선택해 주세요." }
    val base = if (slot in AvatarCatalogue.coveredSlots(costume)) copy(costume = 0) else this
    return with(base) { when (slot) {
        "hair" -> copy(hair = variant); "top" -> copy(top = variant)
        "bottom" -> copy(bottom = variant); "shoes" -> copy(shoes = variant)
        "accessory" -> copy(accessory = variant, headwear = null, ear = null); "eyewear" -> copy(eyewear = variant)
        "wrist" -> copy(wrist = variant); "waist" -> copy(waist = variant)
        "hydration" -> copy(hydration = variant); "face" -> copy(face = variant)
        "socks" -> copy(socks = variant); "headwear" -> copy(headwear = variant)
        "ear" -> copy(ear = variant); "costume" -> copy(costume = variant)
        else -> error("잘못된 아이템 슬롯")
    } }
}
