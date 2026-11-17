package com.onthecrow.wallper.wallpapers.group

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CradleGeometryTest {

    @Test
    fun `a fillet touches its end of the cradle from outside where the outline turns from one to the other`() {
        val cradle = CradleGeometry(radius = 26f, halfStraight = 40f, lift = 4f, fillet = 8f)
        val filletCentre = -cradle.halfWidth to cradle.fillet
        val endCentre = -cradle.halfStraight to -cradle.lift

        assertEquals(cradle.radius + cradle.fillet, distance(filletCentre, endCentre), TOLERANCE)
        // The end's arc starts where the left fillet's ends: at the cradle's angle, and half a turn round from
        // the fillet's.
        val onEnd = pointAt(endCentre, cradle.radius, cradle.filletAngle)
        val onFillet = pointAt(filletCentre, cradle.fillet, cradle.filletAngle - 180f)
        assertEquals(onEnd.first, onFillet.first, TOLERANCE)
        assertEquals(onEnd.second, onFillet.second, TOLERANCE)
        assertTrue(cradle.filletAngle in 90f..180f)
    }

    @Test
    fun `a round cradle without fillets or lift is half a circle`() {
        val cradle = CradleGeometry(radius = 20f, halfStraight = 0f, lift = 0f, fillet = 0f)

        assertTrue(cradle.cuts)
        assertEquals(20f, cradle.halfWidth, TOLERANCE)
        assertEquals(180f, cradle.filletAngle, TOLERANCE)
    }

    @Test
    fun `a pill's cradle is the round one drawn apart by its straight part`() {
        val round = CradleGeometry(radius = 26f, halfStraight = 0f, lift = 4f, fillet = 8f)
        val pill = round.copy(halfStraight = 60f)

        assertEquals(round.halfWidth + 60f, pill.halfWidth, TOLERANCE)
        assertEquals(round.filletAngle, pill.filletAngle, TOLERANCE)
    }

    @Test
    fun `the cradle closes up as the button rises to the edge, and cuts nothing above it`() {
        assertTrue(CradleGeometry(radius = 26f, halfStraight = 30f, lift = 25f, fillet = 8f).cuts)

        // Just touching: nothing beyond the straight part, the fillets flat on the edge.
        val touching = CradleGeometry(radius = 26f, halfStraight = 30f, lift = 26f, fillet = 8f)
        assertFalse(touching.cuts)
        assertEquals(30f, touching.halfWidth, TOLERANCE)
        assertEquals(90f, touching.filletAngle, TOLERANCE)

        assertFalse(CradleGeometry(radius = 26f, halfStraight = 30f, lift = 40f, fillet = 8f).cuts)
        assertFalse(CradleGeometry(radius = 0f, halfStraight = 0f, lift = 0f, fillet = 0f).cuts)
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)

    private fun pointAt(centre: Pair<Float, Float>, radius: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (centre.first + radius * cos(radians).toFloat()) to (centre.second + radius * sin(radians).toFloat())
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
