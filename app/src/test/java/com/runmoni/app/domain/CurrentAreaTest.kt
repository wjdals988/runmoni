package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

/** Administrative-label contracts only; no geocoder, address lookup or actual device position. */
class CurrentAreaTest {
    private val now = weatherInstant("2026-11-11T08:30:00Z")
    private val cell = WeatherLocation(3760, 12702)

    @Test fun administrativeNamesDeduplicateCityVariantsWithoutTakingStreetFields() {
        assertEquals("서울특별시 성북구 길음동", AreaNameFormatter.from(AreaNameParts(
            adminArea = " 서울특별시 ", subAdminArea = "성북구", locality = "서울", subLocality = "길음동",
            countryName = "대한민국", countryCode = "kr")))
        assertEquals("제주특별자치도 제주시", AreaNameFormatter.from(AreaNameParts(
            adminArea = "제주특별자치도", locality = "제주시", countryCode = "KR")))
        // This explicit DTO whitelist is the privacy boundary. Android Address featureName,
        // thoroughfare, subThoroughfare, postalCode and addressLine must not enter it.
        val fields = AreaNameParts::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("adminArea", "subAdminArea", "locality", "subLocality", "countryName", "countryCode"), fields)
    }

    @Test fun foreignNamesRetainCountryAndRemoveRepeatedAdministrativeLevels() {
        assertEquals("United States New York Manhattan", AreaNameFormatter.from(AreaNameParts(
            adminArea = "New York", locality = "NEW YORK", subLocality = "Manhattan",
            countryName = "United States", countryCode = "US")))
        assertEquals("Singapore", AreaNameFormatter.from(AreaNameParts(
            adminArea = "Singapore", locality = "Singapore", countryName = "Singapore", countryCode = "SG")))
        assertNull("A country alone is not a current administrative area", AreaNameFormatter.from(
            AreaNameParts(countryName = "대한민국", countryCode = "KR")))
    }

    @Test fun invalidProviderNamesAreExcludedAndCompleteOutputRemainsBounded() {
        assertEquals("서울 성북", AreaNameFormatter.clean("  서울   성북  "))
        for (invalid in listOf("", "   ", "서울\n성북", "서울\t성북", "서울\u0000", "가".repeat(113))) {
            assertNull("Malformed/overlong name must not appear", AreaNameFormatter.clean(invalid))
        }
        assertEquals("가".repeat(112), AreaNameFormatter.clean("가".repeat(112)))
        assertEquals("가".repeat(48), AreaNameFormatter.from(AreaNameParts(adminArea = "가".repeat(48), countryCode = "KR")))
        assertNull(AreaNameFormatter.from(AreaNameParts(adminArea = "가".repeat(49), countryCode = "KR")))
        assertEquals("합성구", AreaNameFormatter.from(AreaNameParts(
            adminArea = "가".repeat(49), locality = "합성구", countryCode = "KR")))
        assertNull("Never truncate an overlong combined administrative label into a different name",
            AreaNameFormatter.from(AreaNameParts(adminArea = "나".repeat(48), locality = "다".repeat(48),
                countryName = "가".repeat(48), countryCode = "ZZ")))
    }

    @Test fun namedAreasRequireAReadyKnownGridAndValidObservationTime() {
        for (phase in CurrentAreaPhase.entries.filter { it != CurrentAreaPhase.READY }) {
            assertTrue(runCatching { CurrentAreaUi(phase, cell, "서울특별시 성북구 길음동", now) }
                .exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { CurrentAreaUi(CurrentAreaPhase.READY, name = "합성구") }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { CurrentAreaUi(CurrentAreaPhase.READY, cell, " 합성구 ", now) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { CurrentAreaUi(checkedAtMillis = 0) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun freshnessRejectsOldUnknownOrClockSkewedPositionsAndCoordinatesStayApproximate() {
        val area = CurrentAreaUi(CurrentAreaPhase.READY, cell, "서울특별시 성북구 길음동", now)
        assertTrue(area.isRecent(now))
        assertTrue(area.isRecent(now + 15 * 60_000L))
        assertFalse(area.isRecent(now + 15 * 60_000L + 1))
        assertTrue(area.copy(checkedAtMillis = now + 60_000L).isRecent(now))
        assertFalse(area.copy(checkedAtMillis = now + 60_001L).isRecent(now))
        assertFalse(CurrentAreaUi(CurrentAreaPhase.RESOLVING, cell).isRecent(now))
        assertFalse(CurrentAreaUi().isRecent(now))
        val approximate = WeatherLocation.approximate(37.602123, 127.021987).nearbyCoordinates()
        assertEquals("37.60, 127.02 부근", approximate)
        assertFalse(approximate.contains("602123"))
        assertFalse(approximate.contains("021987"))
    }

    @Test fun forecastCannotBorrowAnAreaNameFromADifferentLocationOrWithoutAForecast() {
        val area = CurrentAreaUi(CurrentAreaPhase.READY, cell, "서울특별시 성북구 길음동", now)
        val snapshot = RunningWeatherSnapshot(cell, now, now, now, 15.0, 2.0, 0.0)
        assertEquals(area, RunningWeatherState(WeatherPhase.READY, snapshot, area = area).area)
        assertTrue(runCatching {
            RunningWeatherState(WeatherPhase.READY, snapshot, area = area.copy(location = WeatherLocation(3761, 12702)))
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { RunningWeatherState(area = area) }.exceptionOrNull() is IllegalArgumentException)
    }
}
