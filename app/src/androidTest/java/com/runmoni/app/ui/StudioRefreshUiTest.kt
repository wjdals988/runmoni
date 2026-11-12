package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/**
 * Offline ComponentActivity contracts with in-memory records/actions and inert map tiles.
 * These are not MainActivity, a real run, a photo-picker/MediaStore operation, live runners,
 * Firebase authentication, physical-device tests, or signed-release runtime evidence.
 */
class StudioRefreshUiTest : RunmoniDesignTestBase() {
    /** Check the full measured hit target, not only the already-clipped semantic rectangle. */
    private fun fullyInside(tag: String, scopeTag: String): SemanticsNodeInteraction {
        val action = compose.onNodeWithTag(tag).assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val node = action.fetchSemanticsNode()
        val left = node.positionInRoot.x
        val top = node.positionInRoot.y
        val right = left + node.size.width
        val bottom = top + node.size.height
        val safe = compose.onNodeWithTag(scopeTag).fetchSemanticsNode().boundsInRoot
        assertTrue("Full $tag inside $scopeTag horizontally: $left..$right / $safe",
            left >= safe.left - 1f && right <= safe.right + 1f)
        assertTrue("Full $tag inside $scopeTag vertically: $top..$bottom / $safe",
            top >= safe.top - 1f && bottom <= safe.bottom + 1f)
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val horizontal = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
        var parent = node.parent
        while (parent != null) {
            val bounds = parent.boundsInRoot
            if (vertical.matches(parent)) assertTrue("Full $tag inside its vertical scroll viewport",
                top >= bounds.top - 1f && bottom <= bounds.bottom + 1f)
            if (horizontal.matches(parent)) assertTrue("Full $tag inside its horizontal scroll viewport",
                left >= bounds.left - 1f && right <= bounds.right + 1f)
            parent = parent.parent
        }
        return action
    }

    @Test fun visibleShareLabelKeepsItsFullHeaderTargetAndOpensTheEditorAtLargeText() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val started = maxOf(today, now - 1_200_000L)
        val record = RunRecord("studio-share-fixture", started, maxOf(now, started + 1),
            distanceMeters = 5230.0, activeDurationMillis = 1_200_000L)
        val original = AppState(profile = Profile(name = "검토 러너", mileage = 444), records = listOf(record))
        val state = mutableStateOf(original)
        val viewport = mutableStateOf(Viewport(411, 1f))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, actions) } }

        for (case in listOf(Viewport(411, 1f), Viewport(320, 2f))) {
            compose.runOnIdle { viewport.value = case }
            compose.onNodeWithTag("nav_me").performClick()
            compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
            val share = fullyInside("share_run_record", "record_safe_content").assertIsEnabled()
            compose.onNode(hasText("공유") and hasAnyAncestor(hasTestTag("share_run_record")),
                useUnmergedTree = true).assertIsDisplayed()
            fullyInside("close_record", "record_safe_content")
            val viewportTop = compose.onNodeWithTag("record_scroll_viewport").fetchSemanticsNode().positionInRoot.y
            val shareNode = share.fetchSemanticsNode()
            assertTrue("The full share target stays above the scrolling record body",
                shareNode.positionInRoot.y + shareNode.size.height <= viewportTop + 1f)
            noTextOverflow("studio record share header ${case.width}/${case.scale} ComponentActivity fixture")
            capture("studio-record-share-${case.width}-${case.scale}-fixture")
            share.performClick()
            compose.onNodeWithTag("run_share_page").assertIsDisplayed()
            compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
            compose.onNodeWithTag("nav_me").assertDoesNotExist()
            fullyInside("close_run_share", "run_share_safe_content").performClick()
            fullyInside("share_run_record", "record_safe_content").assertIsEnabled()
            fullyInside("close_record", "record_safe_content").performClick()
            compose.onNodeWithTag("nav_me").assertIsSelected()
        }
        compose.runOnIdle {
            assertEquals(original.profile, state.value.profile)
            assertEquals(original.records, state.value.records)
            assertTrue(actions.exports.isEmpty())
            assertTrue(actions.cloudBackups.isEmpty())
            assertTrue(actions.locationChanges.isEmpty())
        }
    }

    @Test fun largeTextPrimaryJourneysRemainReachableWithoutChangingRecordsOrLocationConsent() {
        val crew = Crew("studio-crew-fixture", "주말 공원 크루", "함께 천천히 달려요",
            region = "서울", paceLabel = "6:30/km", memberCount = 3, isOpen = true)
        val original = AppState(profile = Profile(name = "검토 러너", mileage = 321),
            serverConnected = true, crews = listOf(crew), communityBackend = CommunityBackend.FIREBASE)
        val state = mutableStateOf(original)
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }

        compose.onNodeWithTag("home_start_run").performScrollTo()
        fullyInside("home_start_run", "audit_viewport").assertHeightIsAtLeast(64.dp)
        noTextOverflow("studio home 320/font2 ComponentActivity fixture")
        capture("studio-home-320-font2-fixture")
        fullyInside("home_start_run", "audit_viewport").performClick()
        compose.onNodeWithTag("nav_run").assertIsSelected()
        fullyInside("start_run", "audit_viewport").assertHeightIsAtLeast(64.dp)

        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo()
        fullyInside("open_closet", "audit_viewport").assertHeightIsAtLeast(56.dp)
        noTextOverflow("studio profile 320/font2 ComponentActivity fixture")
        capture("studio-profile-320-font2-fixture")
        fullyInside("open_closet", "audit_viewport").performClick()
        fullyInside("closet_close", "closet_safe_content").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()

        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("rooms_search").performScrollTo().assertIsDisplayed().assert(hasSetTextAction())
        compose.onNodeWithTag("create_crew").performScrollTo()
        fullyInside("create_crew", "audit_viewport").assertHeightIsAtLeast(52.dp)
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().assertIsDisplayed()
        noTextOverflow("studio crew list 320/font2 ComponentActivity fixture")
        capture("studio-crews-320-font2-fixture")

        compose.onNodeWithTag("nav_explore").performClick()
        compose.onNodeWithTag("route_map_viewport").performScrollTo().assertHeightIsAtLeast(96.dp)
        compose.onNodeWithTag("map_options").performScrollTo()
        val map = compose.onNodeWithTag("route_map_viewport").fetchSemanticsNode()
        val options = fullyInside("map_options", "audit_viewport").fetchSemanticsNode()
        assertTrue("Map options are below the measured map pixels, not overlaid on them",
            options.positionInRoot.y >= map.positionInRoot.y + map.size.height - 1f)
        compose.onNodeWithTag("locate_explore").performScrollTo()
        fullyInside("locate_explore", "audit_viewport")
        compose.onNodeWithTag("toggle_runner_sharing").performScrollTo()
        fullyInside("toggle_runner_sharing", "audit_viewport")
        compose.onNodeWithTag("rooms_search").assertDoesNotExist()
        noTextOverflow("studio discovery controls 320/font2 ComponentActivity fixture")
        capture("studio-explore-controls-320-font2-fixture")
        compose.runOnIdle {
            assertEquals(original.profile, state.value.profile)
            assertEquals(original.records, state.value.records)
            assertEquals(original.crews, state.value.crews)
            assertFalse(state.value.publicLocation)
            assertTrue(actions.locationChanges.isEmpty())
            assertTrue(actions.locationRequests.isEmpty())
            assertTrue(actions.sharingConfirmations.isEmpty())
            assertTrue(actions.joinedCrews.isEmpty())
            assertEquals(0, actions.starts)
        }
    }
}
