package com.runmoni.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue

/** Same ScrollBy and geometry as Compose's performScrollTo, bounded to16 attempts.
 * The explicit frame lets a queued test dispatcher apply each real scroll action.
 * It does not change selection, renderer, Ripple, readiness condition or timeout.
 */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.boundedScrollIntoView(
    compose: ComposeContentTestRule,
    tag: String = "target",
): SemanticsNodeInteraction {
    val target = this
    fun delta(leading: Float, trailing: Float): Float =
        if (kotlin.math.sign(leading) == kotlin.math.sign(trailing))
            if (kotlin.math.abs(leading) < kotlin.math.abs(trailing)) leading else trailing
        else 0f
    var lastGeometry = "not observed"
    repeat(16) {
        val node = target.fetchSemanticsNode()
        val scroll = generateSequence(node.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
            ?: throw AssertionError("No scroll ancestor for $tag")
        val coordinates = scroll.layoutInfo.coordinates
        val viewport = coordinates.boundsInParent().translate(
            coordinates.parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero)
        val full = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
        val horizontal = scroll.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val vertical = scroll.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        var x = if (horizontal != null) delta(full.left - viewport.left, full.right - viewport.right) else 0f
        var y = if (vertical != null) delta(full.top - viewport.top, full.bottom - viewport.bottom) else 0f
        if (horizontal?.reverseScrolling == true) x = -x
        if (scroll.layoutInfo.layoutDirection == LayoutDirection.Rtl) x = -x
        if (vertical?.reverseScrolling == true) y = -y
        lastGeometry = "target=$full viewport=$viewport scroll=($x,$y)"
        if (x == 0f && y == 0f) return target.assertIsDisplayed()
        var accepted = false
        compose.onNode(SemanticsMatcher("scroll ancestor for $tag") { it.id == scroll.id },
            useUnmergedTree = true).performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                accepted = action(x, y)
            }
        assertTrue("ScrollBy rejected $tag: $lastGeometry", accepted)
        compose.mainClock.advanceTimeByFrame()
    }
    throw AssertionError("ScrollBy did not reveal $tag in 16 attempts: $lastGeometry")
}
