package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FiltrDatabase
import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.StaffRosterEntity
import com.example.data.remote.SupabaseService
import com.example.domain.attendance.StoreGeofenceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttendanceIntegrationTest {

    private lateinit var db: FiltrDatabase
    private lateinit var repository: FiltrRepository
    private lateinit var repoScope: CoroutineScope
    private lateinit var fakeMetadataStore: ConcurrentHashMap<String, String>
    private lateinit var fakeSupabaseService: SupabaseService

    private val storeLat = 12.9716
    private val storeLng = 77.5946
    private val defaultConfig = StoreGeofenceConfig(
        latitude = storeLat,
        longitude = storeLng,
        radiusMeters = 100.0,
        storeLabel = "FILTR Coffee Indiranagar",
        updatedAt = 1_700_000_000_000L
    )

    private fun offsetLat(northMeters: Double): Double = storeLat + (northMeters / 111_320.0)

    private fun offsetLng(eastMeters: Double): Double =
        storeLng + (eastMeters / (111_320.0 * cos(Math.toRadians(storeLat))))

    private fun createIsolatedSupabaseService(store: ConcurrentHashMap<String, String>): SupabaseService {
        val constructor = SupabaseService::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val service = constructor.newInstance()

        val jsonType = "application/json; charset=utf-8".toMediaType()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (request.method == "GET") {
                val rawQuery = url.encodedQuery.orEmpty()
                val keyParam = rawQuery.split("&")
                    .firstOrNull { it.startsWith("key=eq.") }
                    ?.removePrefix("key=eq.")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()
                val stored = store[keyParam]
                val bodyJson = if (stored != null) {
                    JSONArray().put(
                        JSONObject().apply {
                            put("key", keyParam)
                            put("value", stored)
                        }
                    ).toString()
                } else {
                    "[]"
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody(jsonType))
                    .build()
            } else {
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val bodyObj = JSONObject(buffer.readUtf8())
                val key = bodyObj.getString("key")
                val value = bodyObj.getString("value")
                store[key] = value
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody(jsonType))
                    .build()
            }
        }

        val okClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        SupabaseService::class.java.getDeclaredField("client").apply {
            isAccessible = true
            set(service, okClient)
        }
        SupabaseService::class.java.getDeclaredField("_isInitialized").apply {
            isAccessible = true
            setBoolean(service, true)
        }
        return service
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FiltrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeMetadataStore = ConcurrentHashMap()
        fakeSupabaseService = createIsolatedSupabaseService(fakeMetadataStore)
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = FiltrRepository(
            menuDao = db.menuDao(),
            orderDao = db.orderDao(),
            upiAccountDao = db.upiAccountDao(),
            inventoryDao = db.inventoryDao(),
            customerDao = db.customerDao(),
            fixedCostDao = db.fixedCostDao(),
            checklistDao = db.checklistDao(),
            kitchenInventoryDao = db.kitchenInventoryDao(),
            attendanceDao = db.attendanceDao(),
            supabaseService = fakeSupabaseService,
            externalScope = repoScope
        )
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        db.close()
    }

    @Test
    fun `unconfigured store and offsite check-in are blocked without persisting`() = runTest {
        val unconfigured = StoreGeofenceConfig()
        val unconfiguredRes = repository.recordStaffCheckIn(
            dateKey = "2026-11-25",
            monthKey = "2026-11",
            staffName = "Asha",
            deviceId = "dev-1",
            userLat = storeLat,
            userLng = storeLng,
            config = unconfigured,
            nowMs = 1_700_000_000_000L
        )
        assertTrue(unconfiguredRes.isFailure)
        assertTrue(repository.allAttendanceLogs.first().isEmpty())

        // 180m north of store -> blocked on 100m geofence
        val offsiteRes = repository.recordStaffCheckIn(
            dateKey = "2026-11-25",
            monthKey = "2026-11",
            staffName = "Asha",
            deviceId = "dev-1",
            userLat = offsetLat(180.0),
            userLng = storeLng,
            config = defaultConfig,
            nowMs = 1_700_000_000_000L
        )
        assertTrue(offsiteRes.isFailure)
        val msg = offsiteRes.exceptionOrNull()?.message.orEmpty()
        assertTrue("Expected geofence failure message, got: $msg", msg.contains("Outside FILTR Coffee geofence"))
        assertTrue(msg.contains("allowed 100m"))
        assertTrue(repository.allAttendanceLogs.first().isEmpty())
    }

    @Test
    fun `onsite check-in duplicate check-in prevention and check-out flow`() = runTest {
        val checkInTime = 1_700_000_000_000L
        val checkOutTime = checkInTime + (7 * 60 + 30) * 60_000L // 7h 30m = 450 mins

        // Check-out before check-in fails
        val noCheckInOut = repository.recordStaffCheckOut(
            dateKey = "2026-11-25",
            staffName = "Rohan",
            userLat = storeLat,
            userLng = storeLng,
            config = defaultConfig,
            nowMs = checkOutTime
        )
        assertTrue(noCheckInOut.isFailure)

        // Check-in within ~40m succeeds
        val checkInRes = repository.recordStaffCheckIn(
            dateKey = "2026-11-25",
            monthKey = "2026-11",
            staffName = "Rohan",
            deviceId = "pos-tablet-1",
            userLat = offsetLat(40.0),
            userLng = storeLng,
            config = defaultConfig,
            nowMs = checkInTime
        )
        assertTrue(checkInRes.isSuccess)
        val created = checkInRes.getOrThrow()
        assertEquals("2026-11-25_rohan", created.id)
        assertEquals(checkInTime, created.checkInTimestamp)
        assertEquals(0L, created.checkOutTimestamp)
        assertTrue(created.checkInDistanceMeters in 35.0..45.0)

        // Duplicate check-in (even with different whitespace/casing) is rejected
        val duplicateRes = repository.recordStaffCheckIn(
            dateKey = "2026-11-25",
            monthKey = "2026-11",
            staffName = "  rohan ",
            deviceId = "pos-tablet-2",
            userLat = storeLat,
            userLng = storeLng,
            config = defaultConfig,
            nowMs = checkInTime + 60_000L
        )
        assertTrue(duplicateRes.isFailure)
        assertEquals("Already checked in today", duplicateRes.exceptionOrNull()?.message)

        // Off-site check-out (~150m east) is blocked and does not overwrite check-in
        val offsiteOut = repository.recordStaffCheckOut(
            dateKey = "2026-11-25",
            staffName = "Rohan",
            userLat = storeLat,
            userLng = offsetLng(150.0),
            config = defaultConfig,
            nowMs = checkOutTime
        )
        assertTrue(offsiteOut.isFailure)
        assertTrue(offsiteOut.exceptionOrNull()?.message.orEmpty().contains("Outside FILTR Coffee geofence"))
        assertEquals(0L, db.attendanceDao().getLogById("2026-11-25_rohan")?.checkOutTimestamp)

        // On-site check-out (~30m east) succeeds and computes workedMinutes
        val checkOutRes = repository.recordStaffCheckOut(
            dateKey = "2026-11-25",
            staffName = "Rohan",
            userLat = storeLat,
            userLng = offsetLng(30.0),
            config = defaultConfig,
            nowMs = checkOutTime
        )
        assertTrue(checkOutRes.isSuccess)
        val completed = checkOutRes.getOrThrow()
        assertEquals(checkOutTime, completed.checkOutTimestamp)
        assertEquals(450, completed.workedMinutes)
        assertEquals(checkOutTime, completed.updatedAt)

        // Duplicate check-out is rejected
        val dupCheckOut = repository.recordStaffCheckOut(
            dateKey = "2026-11-25",
            staffName = "Rohan",
            userLat = storeLat,
            userLng = storeLng,
            config = defaultConfig,
            nowMs = checkOutTime + 60_000L
        )
        assertTrue(dupCheckOut.isFailure)

        val monthLogs = repository.getAttendanceLogsForMonth("2026-11").first()
        assertEquals(1, monthLogs.size)
        assertEquals(450, monthLogs.first().workedMinutes)
    }

    @Test
    fun `store geofence config round trips via Supabase metadata JSON`() = runTest {
        val saveRes = repository.saveAttendanceStoreConfig(defaultConfig)
        assertTrue(saveRes.isSuccess)

        val fetched = repository.fetchAttendanceStoreConfig()
        assertNotNull(fetched)
        assertEquals(defaultConfig.latitude, fetched!!.latitude, 0.00001)
        assertEquals(defaultConfig.longitude, fetched.longitude, 0.00001)
        assertEquals(defaultConfig.radiusMeters, fetched.radiusMeters, 0.01)
        assertEquals(defaultConfig.storeLabel, fetched.storeLabel)
        assertEquals(defaultConfig.updatedAt, fetched.updatedAt)
    }

    @Test
    fun `reconcileAttendanceFromCloud merges newer cloud and pushes newer local logs`() = runTest {
        val dao = db.attendanceDao()

        // Local has:
        // 1) Older entry for Rohan on Nov 20 (updatedAt = 1000) -> cloud (updatedAt = 5000) should win
        // 2) Newer entry for Asha on Nov 21 (updatedAt = 8000) -> local should win and trigger upload
        // 3) Local-only entry for Vikram on Nov 22 (updatedAt = 9000) -> local preserved and uploaded
        dao.upsertLogs(
            listOf(
                AttendanceLogEntity(
                    id = "2026-11-20_rohan",
                    dateKey = "2026-11-20",
                    monthKey = "2026-11",
                    staffName = "Rohan",
                    checkInTimestamp = 1000L,
                    checkOutTimestamp = 0L,
                    workedMinutes = 0,
                    updatedAt = 1000L
                ),
                AttendanceLogEntity(
                    id = "2026-11-21_asha",
                    dateKey = "2026-11-21",
                    monthKey = "2026-11",
                    staffName = "Asha",
                    checkInTimestamp = 2000L,
                    checkOutTimestamp = 8000L,
                    workedMinutes = 360,
                    updatedAt = 8000L
                ),
                AttendanceLogEntity(
                    id = "2026-11-22_vikram",
                    dateKey = "2026-11-22",
                    monthKey = "2026-11",
                    staffName = "Vikram",
                    checkInTimestamp = 9000L,
                    updatedAt = 9000L
                )
            )
        )

        // Cloud has:
        // 1) Newer Rohan (updatedAt = 5000, checked out with 480m)
        // 2) Older Asha (updatedAt = 3000, not checked out)
        // 3) Cloud-only Priya on Nov 23 (updatedAt = 7000)
        val cloudArr = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "2026-11-20_rohan")
                put("dateKey", "2026-11-20")
                put("monthKey", "2026-11")
                put("staffName", "Rohan")
                put("checkInTimestamp", 1000L)
                put("checkOutTimestamp", 5000L)
                put("workedMinutes", 480)
                put("updatedAt", 5000L)
            })
            put(JSONObject().apply {
                put("id", "2026-11-21_asha")
                put("dateKey", "2026-11-21")
                put("monthKey", "2026-11")
                put("staffName", "Asha")
                put("checkInTimestamp", 2000L)
                put("checkOutTimestamp", 0L)
                put("workedMinutes", 0)
                put("updatedAt", 3000L)
            })
            put(JSONObject().apply {
                put("id", "2026-11-23_priya")
                put("dateKey", "2026-11-23")
                put("monthKey", "2026-11")
                put("staffName", "Priya")
                put("checkInTimestamp", 7000L)
                put("updatedAt", 7000L)
            })
        }
        fakeMetadataStore["attendance_month_2026-11"] = cloudArr.toString()

        repository.reconcileAttendanceFromCloud("2026-11")

        // Verify Room merged state
        val rohan = dao.getLogById("2026-11-20_rohan")!!
        assertEquals(5000L, rohan.updatedAt)
        assertEquals(480, rohan.workedMinutes)

        val asha = dao.getLogById("2026-11-21_asha")!!
        assertEquals(8000L, asha.updatedAt)
        assertEquals(360, asha.workedMinutes)

        val vikram = dao.getLogById("2026-11-22_vikram")!!
        assertEquals(9000L, vikram.updatedAt)

        val priya = dao.getLogById("2026-11-23_priya")!!
        assertEquals(7000L, priya.updatedAt)

        // Verify cloud was updated with merged 4 entries including newer Asha & Vikram
        val uploadedJson = fakeMetadataStore["attendance_month_2026-11"]!!
        val uploadedArr = JSONArray(uploadedJson)
        assertEquals(4, uploadedArr.length())
        val uploadedMap = (0 until uploadedArr.length())
            .map { uploadedArr.getJSONObject(it) }
            .associateBy { it.getString("id") }
        assertEquals(480, uploadedMap["2026-11-20_rohan"]!!.getInt("workedMinutes"))
        assertEquals(360, uploadedMap["2026-11-21_asha"]!!.getInt("workedMinutes"))
        assertEquals(9000L, uploadedMap["2026-11-22_vikram"]!!.getLong("updatedAt"))
        assertEquals(7000L, uploadedMap["2026-11-23_priya"]!!.getLong("updatedAt"))
    }

    @Test
    fun `saveStaffRosterMember filters activeStaffRoster and reconcileStaffRosterFromCloud merges by updatedAt`() = runTest {
        val dao = db.attendanceDao()

        // 1. Save active and inactive members through repository
        val saveRohan = repository.saveStaffRosterMember(
            StaffRosterEntity(
                id = "rohan",
                name = "  Rohan ",
                roleTitle = "Barista",
                shiftLabel = "Morning",
                weeklyOffDayOfWeek = Calendar.SUNDAY,
                joinedDateKey = "2026-11-01",
                isActive = true,
                updatedAt = 1000L
            )
        )
        assertTrue(saveRohan.isSuccess)

        val saveInactive = repository.saveStaffRosterMember(
            StaffRosterEntity(
                id = "kiran",
                name = "Kiran",
                roleTitle = "Helper",
                shiftLabel = "Evening",
                weeklyOffDayOfWeek = Calendar.MONDAY,
                joinedDateKey = "2026-10-01",
                isActive = false,
                updatedAt = 0L // Should auto-populate > 0L
            )
        )
        assertTrue(saveInactive.isSuccess)

        val saveAsha = repository.saveStaffRosterMember(
            StaffRosterEntity(
                id = "asha",
                name = "Asha",
                roleTitle = "Manager",
                shiftLabel = "Full Day",
                weeklyOffDayOfWeek = Calendar.WEDNESDAY,
                joinedDateKey = "2026-10-15",
                isActive = true,
                updatedAt = 8000L
            )
        )
        assertTrue(saveAsha.isSuccess)

        val activeList = repository.activeStaffRoster.first()
        assertEquals(listOf("Asha", "Rohan"), activeList.map { it.name })
        assertTrue(dao.getRosterMemberById("kiran")!!.updatedAt > 0L)

        // 2. Seed cloud roster:
        //    - rohan (updatedAt = 5000 > local 1000) -> cloud wins in Room
        //    - asha (updatedAt = 3000 < local 8000) -> local wins and pushes to cloud
        //    - priya (cloud-only, updatedAt = 7000) -> inserted into Room
        val cloudArr = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "rohan")
                put("name", "Rohan")
                put("roleTitle", "Senior Barista")
                put("shiftLabel", "Full Day")
                put("weeklyOffDayOfWeek", Calendar.TUESDAY)
                put("joinedDateKey", "2026-11-01")
                put("isActive", true)
                put("updatedAt", 5000L)
            })
            put(JSONObject().apply {
                put("id", "asha")
                put("name", "Asha")
                put("roleTitle", "Barista")
                put("shiftLabel", "Morning")
                put("weeklyOffDayOfWeek", 0)
                put("joinedDateKey", "2026-10-15")
                put("isActive", true)
                put("updatedAt", 3000L)
            })
            put(JSONObject().apply {
                put("id", "priya")
                put("name", "Priya")
                put("roleTitle", "Kitchen")
                put("shiftLabel", "Morning")
                put("weeklyOffDayOfWeek", Calendar.FRIDAY)
                put("joinedDateKey", "2026-11-10")
                put("isActive", true)
                put("updatedAt", 7000L)
            })
        }
        // Drain background roster sync jobs launched by saveStaffRosterMember before seeding cloud state
        repoScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        fakeMetadataStore["attendance_staff_roster"] = cloudArr.toString()

        repository.reconcileStaffRosterFromCloud()

        val rohan = dao.getRosterMemberById("rohan")!!
        assertEquals(5000L, rohan.updatedAt)
        assertEquals("Senior Barista", rohan.roleTitle)
        assertEquals(Calendar.TUESDAY, rohan.weeklyOffDayOfWeek)

        val asha = dao.getRosterMemberById("asha")!!
        assertEquals(8000L, asha.updatedAt)
        assertEquals("Manager", asha.roleTitle)
        assertEquals(Calendar.WEDNESDAY, asha.weeklyOffDayOfWeek)

        val priya = dao.getRosterMemberById("priya")!!
        assertEquals(7000L, priya.updatedAt)
        assertEquals("Kitchen", priya.roleTitle)

        // Verify cloud was updated with merged 4 entries (asha, kiran, priya, rohan)
        val uploadedJson = fakeMetadataStore["attendance_staff_roster"]!!
        val uploadedArr = JSONArray(uploadedJson)
        assertEquals(4, uploadedArr.length())
        val uploadedMap = (0 until uploadedArr.length())
            .map { uploadedArr.getJSONObject(it) }
            .associateBy { it.getString("id") }
        assertEquals("Senior Barista", uploadedMap["rohan"]!!.getString("roleTitle"))
        assertEquals("Manager", uploadedMap["asha"]!!.getString("roleTitle"))
        assertEquals(8000L, uploadedMap["asha"]!!.getLong("updatedAt"))
        assertEquals(7000L, uploadedMap["priya"]!!.getLong("updatedAt"))
        assertTrue(!uploadedMap["kiran"]!!.getBoolean("isActive"))

        // 3. Verify reconcileAttendanceFromCloud also reconciles staff roster updates
        val updatedCloudArr = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "priya")
                put("name", "Priya")
                put("roleTitle", "Head Barista")
                put("shiftLabel", "Full Day")
                put("weeklyOffDayOfWeek", Calendar.SATURDAY)
                put("joinedDateKey", "2026-11-10")
                put("isActive", true)
                put("updatedAt", 9500L)
            })
        }
        fakeMetadataStore["attendance_staff_roster"] = updatedCloudArr.toString()
        repository.reconcileAttendanceFromCloud("2026-11")
        val priyaAfterMonthReconcile = dao.getRosterMemberById("priya")!!
        assertEquals(9500L, priyaAfterMonthReconcile.updatedAt)
        assertEquals("Head Barista", priyaAfterMonthReconcile.roleTitle)
        assertEquals(Calendar.SATURDAY, priyaAfterMonthReconcile.weeklyOffDayOfWeek)
    }
}
