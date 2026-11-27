package com.runmoni.app.domain

/** The separately fitted collection. Existing catalogue indexes remain stable. */
object StudioCollection {
    data class Item(val slot: String, val variant: Int, val label: String, val sharedVariant: Int) {
        val assetKey get() = "$slot-$variant"
        val tintRole get() = when (slot) {
            "top" -> 1
            "bottom" -> 2
            "shoes" -> 3
            else -> 0
        }
    }

    val items = listOf(
        Item("headwear", 7, "레이스 선바이저", 2),
        Item("headwear", 8, "나이트 패널 캡", 1),
        Item("headwear", 9, "트레일 헤드밴드", 3),
        Item("top", 10, "레이스 싱글릿", 0),
        Item("top", 11, "나이트 방풍 셸", 3),
        Item("top", 12, "트레일 기능성 티", 1),
        Item("bottom", 9, "레이스 스플릿 하의", 0),
        Item("bottom", 10, "나이트 컴프레션 타이츠", 1),
        Item("bottom", 11, "트레일 카고 하의", 0),
        Item("shoes", 8, "레이스 폼 러닝화", 1),
        Item("shoes", 9, "나이트 리플렉트 러닝화", 2),
        Item("shoes", 10, "트레일 러그 러닝화", 3),
        Item("hydration", 7, "듀얼 플라스크 조끼", 1),
        Item("waist", 7, "레이스 젤 벨트", 1),
        Item("eyewear", 7, "나이트 로우프로파일 실드", 3),
    )

    private val bySlot = items.groupBy(Item::slot)
    fun inSlot(slot: String): List<Item> = bySlot[slot].orEmpty()
    fun item(slot: String, variant: Int): Item? = inSlot(slot).firstOrNull { it.variant == variant }
}
