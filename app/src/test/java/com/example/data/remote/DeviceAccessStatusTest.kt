package com.example.data.remote

import com.example.data.entity.StaffRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAccessStatusTest {

    @Test
    fun `owner restore promotes local REVOKED to ACTIVE when remote status is ACTIVE`() {
        assertEquals("ACTIVE", SupabaseService.resolveDeviceAccessStatus("ACTIVE", "REVOKED"))
    }

    @Test
    fun `owner approval promotes local PENDING to ACTIVE when remote status is ACTIVE`() {
        assertEquals("ACTIVE", SupabaseService.resolveDeviceAccessStatus("ACTIVE", "PENDING"))
    }

    @Test
    fun `remote ACTIVE preserves local UNVERIFIED until passcode entered`() {
        assertEquals("UNVERIFIED", SupabaseService.resolveDeviceAccessStatus("ACTIVE", "UNVERIFIED"))
    }

    @Test
    fun `remote REVOKED overrides local ACTIVE`() {
        assertEquals("REVOKED", SupabaseService.resolveDeviceAccessStatus("REVOKED", "ACTIVE"))
    }

    @Test
    fun `remote PENDING overrides local ACTIVE`() {
        assertEquals("PENDING", SupabaseService.resolveDeviceAccessStatus("PENDING", "ACTIVE"))
    }

    @Test
    fun `missing remote record preserves local status`() {
        assertEquals("ACTIVE", SupabaseService.resolveDeviceAccessStatus(null, "ACTIVE"))
    }

    @Test
    fun `remote STAFF with newer timestamp demotes local ADMIN to STAFF`() {
        val (role, ts) = SupabaseService.resolveDeviceRole("STAFF", 2_000L, "ADMIN", 1_000L)
        assertEquals("STAFF", role)
        assertEquals(2_000L, ts)
    }

    @Test
    fun `remote ADMIN with newer timestamp promotes local STAFF to ADMIN`() {
        val (role, ts) = SupabaseService.resolveDeviceRole("ADMIN", 5_000L, "STAFF", 3_000L)
        assertEquals("ADMIN", role)
        assertEquals(5_000L, ts)
    }

    @Test
    fun `local ADMIN with newer timestamp beats older remote STAFF`() {
        val (role, ts) = SupabaseService.resolveDeviceRole("STAFF", 1_000L, "ADMIN", 4_000L)
        assertEquals("ADMIN", role)
        assertEquals(4_000L, ts)
    }

    @Test
    fun `null or unknown remote role preserves normalized local role`() {
        val (roleNull, tsNull) = SupabaseService.resolveDeviceRole(null, 0L, "admin", 500L)
        assertEquals("ADMIN", roleNull)
        assertEquals(500L, tsNull)

        val (roleBad, tsBad) = SupabaseService.resolveDeviceRole("UNKNOWN", 999L, "other", 500L)
        assertEquals("STAFF", roleBad)
        assertEquals(999L, tsBad)
    }

    @Test
    fun `resolveDeviceMemberLink prefers newer remote assignment and enforces roster constraints`() {
        val (mId, mName, mTs) = SupabaseService.resolveDeviceMemberLink(
            remoteMemberId = "kanha",
            remoteMemberName = "Kanha",
            remoteUpdatedAtMs = 5_000L,
            localMemberId = "",
            localMemberName = "",
            localUpdatedAtMs = 0L
        )
        assertEquals("kanha", mId)
        assertEquals("Kanha", mName)
        assertEquals(5_000L, mTs)

        val inactiveStaff = StaffRosterEntity(id = "kanha", name = "Kanha", isActive = false, orgRole = "STAFF")
        assertEquals("STAFF" to "REVOKED", SupabaseService.enforceMemberRosterPolicy("ADMIN", "ACTIVE", inactiveStaff))

        val activeStaff = StaffRosterEntity(id = "kanha", name = "Kanha", isActive = true, orgRole = "STAFF")
        assertEquals("STAFF" to "ACTIVE", SupabaseService.enforceMemberRosterPolicy("ADMIN", "ACTIVE", activeStaff))

        assertEquals("ADMIN" to "ACTIVE", SupabaseService.enforceMemberRosterPolicy("ADMIN", "ACTIVE", null))
    }
}
