package com.runmoni.app.domain

/** Map inks are separate from garments and UI; style changes never reload sources. */
internal data class RunningMapPalette(
    val ground: Int, val road: Int, val park: Int, val water: Int, val ink: Int,
    val secondary: Int, val route: Int, val roadEdge: Int, val halo: Int, val dark: Boolean,
) {
    companion object {
        fun forDark(dark: Boolean) = if (dark) RunningMapPalette(
            0xFF182930.toInt(), 0xFF344B54.toInt(), 0xFF243F36.toInt(), 0xFF203F50.toInt(),
            0xFFE2ECEF.toInt(), 0xFFB5C7CF.toInt(), 0xFF8ACFD6.toInt(), 0xFF20353E.toInt(),
            0xFF142128.toInt(), true,
        ) else RunningMapPalette(
            RunningMapColors.ground, RunningMapColors.road, RunningMapColors.park, RunningMapColors.water,
            RunningMapColors.ink, RunningMapColors.secondary, RunningMapColors.route, RunningMapColors.roadEdge,
            0xFFFFFFFF.toInt(), false,
        )
    }
}
