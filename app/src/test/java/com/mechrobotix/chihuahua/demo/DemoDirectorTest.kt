package com.mechrobotix.chihuahua.demo

import com.mechrobotix.chihuahua.audio.NarrationEvent
import com.mechrobotix.chihuahua.data.DestinationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DemoDirectorTest {
    @Test
    fun demoRepeatsSequenceUntilCancelled() = runBlocking {
        val destinations = DestinationRepository.destinations.take(2)
        val sequence = destinations.map { DemoStep(it.id, holdAfterNarrationMs = 1L) }
        val visited = mutableListOf<String>()
        val firstRepeatedDestination = CompletableDeferred<Unit>()

        val director = DemoDirector(
            destinations = destinations,
            sequence = sequence,
            scope = this,
            onTravelToDestination = { _, destination, _, _ ->
                visited += destination.id
                if (visited.size >= 3) firstRepeatedDestination.complete(Unit)
            },
            onPresentDestination = { _, _, _ -> Unit },
            onNarrateAndAwait = { NarrationEvent.Completed(it) },
        )

        director.start()
        withTimeout(1_000L) { firstRepeatedDestination.await() }
        director.cancel()

        assertEquals(
            listOf(destinations[0].id, destinations[1].id, destinations[0].id),
            visited.take(3),
        )
        assertFalse(director.isRunning)
    }
}
