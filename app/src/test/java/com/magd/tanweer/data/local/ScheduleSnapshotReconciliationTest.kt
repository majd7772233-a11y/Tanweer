package com.magd.tanweer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleSnapshotReconciliationTest {
    private lateinit var database: TanweerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TanweerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun slot(
        id: String,
        subjectId: String,
        syncStatus: String = "SYNCED"
    ) = ScheduleSlotEntity(
        id = id,
        groupId = "class_10_B",
        dayOfWeek = 1,
        slotOrder = 1,
        subjectId = subjectId,
        subjectName = subjectId,
        subjectIcon = "📚",
        colorHex = null,
        startTime = "08:00",
        endTime = "08:45",
        syncStatus = syncStatus
    )

    @Test
    fun reconciliationReplacesServerRowsAndPreservesEveryLocalMutation() = runBlocking {
        database.scheduleDao().insertSlots(
            listOf(
                slot("old_server", "old_math"),
                slot("local_pending", "local_science", "PENDING"),
                slot("local_syncing", "local_arabic", "SYNCING"),
                slot("local_failed", "local_english", "FAILED")
            )
        )

        database.scheduleDao().reconcileGroupSchedule(
            "class_10_B",
            listOf(slot("server_fresh", "server_physics"))
        )

        val actual = database.scheduleDao().getSlotsSync("class_10_B")
        val ids = actual.map { it.id }.toSet()

        assertTrue("Old server snapshot must be replaced", "old_server" !in ids)
        assertTrue("Fresh server snapshot must be present", "server_fresh" in ids)
        assertTrue("Pending local slot must survive", "local_pending" in ids)
        assertTrue("In-flight local slot must survive", "local_syncing" in ids)
        assertTrue("Failed local slot must survive", "local_failed" in ids)
        assertEquals(4, actual.size)
    }

    @Test
    fun emptyServerSnapshotDoesNotLeaveScheduleBlankWhenLocalMutationsExist() = runBlocking {
        database.scheduleDao().insertSlots(
            listOf(
                slot("old_server", "old_math"),
                slot("local_pending", "local_science", "PENDING")
            )
        )

        database.scheduleDao().reconcileGroupSchedule("class_10_B", emptyList())

        val actual = database.scheduleDao().getSlotsSync("class_10_B")
        assertEquals(listOf("local_pending"), actual.map { it.id })
    }
}
