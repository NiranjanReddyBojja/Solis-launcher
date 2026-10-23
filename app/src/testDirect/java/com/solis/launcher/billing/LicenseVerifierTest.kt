package com.solis.launcher.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures were produced by server/stripe-checkout (gen-license-keys.mjs + signLicense) with a throwaway key. */
class LicenseVerifierTest {

    private val verifier = LicenseVerifier(PUBLIC_KEY)

    @Test
    fun acceptsLicenceSignedByServer() = assertTrue(verifier.isValid(LICENSE))

    @Test
    fun rejectsTamperedPayload() {
        val (payload, sig) = LICENSE.split('.')
        val forged = payload.dropLast(1) + (if (payload.last() == 'A') 'B' else 'A')
        assertFalse(verifier.isValid("$forged.$sig"))
    }

    @Test
    fun rejectsGarbageAndMissing() {
        assertFalse(verifier.isValid(null))
        assertFalse(verifier.isValid(""))
        assertFalse(verifier.isValid("not-a-licence"))
        assertFalse(verifier.isValid("a.b.c"))
    }

    @Test
    fun unconfiguredBuildAcceptsNothing() {
        val empty = LicenseVerifier("")
        assertFalse(empty.isConfigured)
        assertFalse(empty.isValid(LICENSE))
    }

    @Test
    fun rawToDerHandlesHighBitAndLeadingZeros() {
        val raw = ByteArray(64).also { it[0] = 0x80.toByte(); it[33] = 0x01 }
        val der = LicenseVerifier.rawToDer(raw)
        // r = 0x80 00..00 needs a 0x00 pad (33 bytes); s has a leading zero byte stripped (31 bytes).
        assertEquals(0x30, der[0].toInt())
        assertEquals(33, der[3].toInt())
        assertEquals(31, der[3 + 33 + 2].toInt())
    }

    private companion object {
        const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJEqejipXQYlimF+Eyx8IUPYcsGDjk8pPSVBMSah3s0FiuYa4MpkL6pRoxnHMBD/wQxrD8MT3/+AatSmi+IzqSA=="
        const val LICENSE = "eyJ2IjoxLCJwcm9kdWN0Ijoic29saXNfcHJvX2xpZmV0aW1lIiwic2lkIjoiY3NfdGVzdF9hYmMiLCJpYXQiOjE3NjEyNTkwMDB9.ABc2l_Umj9fp7oqo_WIi-FpenczHE1WTj-v6KjUUOWkESvNbfJOVTWeBX9xD85_gFaYaqxTTLFxojyYNdpHFrQ"
    }
}
