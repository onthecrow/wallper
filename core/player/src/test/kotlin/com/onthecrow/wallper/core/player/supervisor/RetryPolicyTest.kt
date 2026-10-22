package com.onthecrow.wallper.core.player.supervisor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryPolicyTest {

    @Test
    fun `retries are immediate, then delayed, then spent`() {
        val policy = RetryPolicy({ 0L })

        assertEquals(listOf<Long?>(0, 1_000, 3_000, 5_000), List(4) { policy.acquire() })
        assertNull(policy.acquire())
    }

    @Test
    fun `a request long after the last granted one starts over`() {
        var now = 0L
        val policy = RetryPolicy({ now })
        repeat(4) { policy.acquire() }

        now += 29_999
        assertNull(policy.acquire())

        now += 1
        assertEquals(0L, policy.acquire())
    }
}
