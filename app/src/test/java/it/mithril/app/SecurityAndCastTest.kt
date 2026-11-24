package it.mithril.app

import it.mithril.app.cast.LocalMediaServer
import it.mithril.app.update.UpdateVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class SecurityAndCastTest {
    private fun signEnvelope(payloadJson: String): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = kpg.generateKeyPair()
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private)
            update(payloadBytes)
        }.sign()
        val enc = Base64.getEncoder()
        val env = """{"payload":"${enc.encodeToString(payloadBytes)}","signature":"${enc.encodeToString(sig)}"}"""
        val pub = enc.encodeToString(pair.public.encoded)
        return env to pub
    }

    @Test fun updateVerifierAcceptsValidSignedManifest() {
        val sha = "a".repeat(64)
        val payload = """{"versionName":"1.7.0","versionCode":13,"minSdk":26,"apkUrl":"https://github.com/Adrianss31/mithril/releases/download/v1.7.0/mithril-1.7.0.apk","size":6000000,"sha256":"$sha","notes":"Note"}"""
        val (doc, pub) = signEnvelope(payload)
        val info = UpdateVerifier.verify(doc, pub)
        assertEquals("1.7.0", info.versionName)
        assertEquals(13L, info.versionCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateVerifierRejectsWrongHost() {
        val sha = "a".repeat(64)
        val payload = """{"versionName":"1.7.0","versionCode":13,"minSdk":26,"apkUrl":"https://evil.example.com/Adrianss31/mithril/releases/download/v1.7.0/a.apk","size":6000000,"sha256":"$sha","notes":""}"""
        val (doc, pub) = signEnvelope(payload)
        UpdateVerifier.verify(doc, pub)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateVerifierRejectsTamperedSignature() {
        val sha = "a".repeat(64)
        val payload = """{"versionName":"1.7.0","versionCode":13,"minSdk":26,"apkUrl":"https://github.com/Adrianss31/mithril/releases/download/v1.7.0/mithril-1.7.0.apk","size":6000000,"sha256":"$sha","notes":""}"""
        val (doc, pub) = signEnvelope(payload)
        val otherPub = signEnvelope(payload).second
        UpdateVerifier.verify(doc, otherPub)
    }

    @Test fun byteRangeParsingHandlesValidAndInvalidHeaders() {
        assertEquals(0L..999L, LocalMediaServer.parseByteRange(null, 1000L))
        assertEquals(100L..999L, LocalMediaServer.parseByteRange("bytes=100-", 1000L))
        assertEquals(100L..250L, LocalMediaServer.parseByteRange("bytes=100-250", 1000L))
        assertEquals(100L..999L, LocalMediaServer.parseByteRange("bytes=100-5000", 1000L))
        assertEquals(800L..999L, LocalMediaServer.parseByteRange("bytes=-200", 1000L))
        assertNull(LocalMediaServer.parseByteRange("bytes=1000-", 1000L))
        assertNull(LocalMediaServer.parseByteRange("bytes=0-10,20-30", 1000L))
        assertNull(LocalMediaServer.parseByteRange("bytes=abc-10", 1000L))
        assertNull(LocalMediaServer.parseByteRange("bytes=50-10", 1000L))
        assertNull(LocalMediaServer.parseByteRange(null, -1L))
    }
}
