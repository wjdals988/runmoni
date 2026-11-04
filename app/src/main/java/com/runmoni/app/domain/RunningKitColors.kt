package com.runmoni.app.domain

/** Fabric settings for the approved base outfit, independent of inventory item IDs. */
enum class RunningKitRegion(val label: String, val wireKey: String) {
    TOP("상의", "top"),
    BOTTOM("하의", "bottom"),
    SHOES("신발", "shoes"),
}

data class RunningKitColors(val top: Int = 0, val bottom: Int = 0, val shoes: Int = 0) {
    fun color(region: RunningKitRegion): Int = when (region) {
        RunningKitRegion.TOP -> top
        RunningKitRegion.BOTTOM -> bottom
        RunningKitRegion.SHOES -> shoes
    }

    fun withColor(region: RunningKitRegion, color: Int): RunningKitColors {
        require(color in 0 until COLOR_COUNT) { "올바른 러닝복 색상을 선택해 주세요." }
        return when (region) {
            RunningKitRegion.TOP -> copy(top = color)
            RunningKitRegion.BOTTOM -> copy(bottom = color)
            RunningKitRegion.SHOES -> copy(shoes = color)
        }
    }

    fun normalized() = RunningKitColors(checked(top), checked(bottom), checked(shoes))
    val isOriginal: Boolean get() = checked(top) == 0 && checked(bottom) == 0 && checked(shoes) == 0

    companion object {
        const val COLOR_COUNT = 6
        private fun checked(value: Int) = value.takeIf { it in 0 until COLOR_COUNT } ?: 0
    }
}
