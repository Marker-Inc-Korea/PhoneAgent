package ai.markr.phoneagent

import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.markr.phoneagent.data.KeystoreCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreCryptoInstrumentedTest {

    private val crypto = KeystoreCrypto()

    @Test fun round_trips_value() { // AC-7
        val plain = "sk-test-한글-키-12345"
        val enc = crypto.encrypt(plain)
        assertNotEquals(plain, enc)
        assertEquals(plain, crypto.decrypt(enc))
    }

    @Test fun empty_stays_empty() {
        assertEquals("", crypto.encrypt(""))
        assertEquals("", crypto.decrypt(""))
    }

    @Test fun ciphertext_differs_each_time() {
        val a = crypto.encrypt("same")
        val b = crypto.encrypt("same")
        assertNotEquals(a, b) // random IV per encryption
        assertTrue(crypto.decrypt(a) == "same" && crypto.decrypt(b) == "same")
    }
}
