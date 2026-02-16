package org.example.memosm.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.testcontainers.containers.GenericContainer
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class MemosApiIntegrationTest(private val dockerImageName: String) {

    private lateinit var container: GenericContainer<*>
    private lateinit var baseUrl: String

    companion object {
        const val TEST_USERNAME = "adminuser"
        const val TEST_PASSWORD = "password123"
        const val TEST_DISPLAY_NAME = "Admin"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<String>> {
            return listOf(
                arrayOf("neosmemo/memos:canary"),
                arrayOf("neosmemo/memos:stable"),
                arrayOf("neosmemo/memos:0.26"),
                arrayOf("neosmemo/memos:0.26.1"),
                arrayOf("neosmemo/memos:0.26.0")
            )
        }
    }

    @Before
    fun setUp() {
        val logFile = java.io.File("/tmp/memos_debug.log")
        // Reset log file
        logFile.writeText("DEBUG: setUp() started for $dockerImageName\n")

        fun log(msg: String) {
            logFile.appendText("$msg\n")
            println(msg) // Also print to stdout just in case
        }

        log("DEBUG: CWD = ${java.io.File(".").absolutePath}")
        log("DEBUG: User = ${System.getProperty("user.name")}")

        try {
            // Start Memos container
            container = GenericContainer(dockerImageName)
            log("DEBUG: Attempting container.start() for $dockerImageName")

            container.withExposedPorts(5230).withEnv("MEMOS_DRIVER", "sqlite").start()

            log("DEBUG: container.start() returned")

        } catch (e: Exception) {
            log("ERROR: Exception caught in setUp: ${e.message}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            log(sw.toString())
            throw e
        }

        baseUrl = "http://${container.host}:${container.getMappedPort(5230)}/"
    }

    @After
    fun tearDown() {
        container.stop()
    }

    @Test
    fun testLoginFlow() = runBlocking {
        println("Running testLoginFlow against $dockerImageName")
        // 1. Sign up (create admin user)
        // Since this is a fresh instance, the first user is admin
        val logging = okhttp3.logging.HttpLoggingInterceptor { message ->
            println(message)
        }.apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }


        val client =
            OkHttpClient.Builder().addInterceptor(logging).readTimeout(30, TimeUnit.SECONDS).build()

        val signupJson = """
            {
                "username": "$TEST_USERNAME",
                "password": "$TEST_PASSWORD",
                "displayName": "$TEST_DISPLAY_NAME",
                "role": "HOST"
            }
        """.trimIndent()

        val signupRequest = Request.Builder().url("${baseUrl}api/v1/users")
            .post(signupJson.toRequestBody("application/json".toMediaType())).build()

        client.newCall(signupRequest).execute().use { response ->
            val body = response.body.string()

            // For 0.25, api/v1/users returns the user object, not empty or different structure?
            // Step 169 showed it returns the User object.
            println("Signup response: $body")
            assertTrue("Signup failed: $body", response.isSuccessful)
        }

        // 2. Verify Instance Profile
        val api = MemosApiFactory.create(baseUrl, client)
        val profile = api.getInstanceProfile()
        println("Instance version: ${profile.version}")
        assertNotNull(profile.version)
        // Adjust assertion based on actual version string format if needed
        // assertTrue(profile.version!!.startsWith("0.26")) 

        // 3. Login using helper function (simulating LoginScreen behavior)
        try {
            // Pass client to share CookieJar
            val token = loginAndCreateToken(api, baseUrl, TEST_USERNAME, TEST_PASSWORD)
            println("Generated Token: $token")
            assertNotNull(token)
            assertTrue(token.isNotEmpty())

            // 4. Verify Token Login
            val authClient = OkHttpClient.Builder().addInterceptor { chain ->
                val request =
                    chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
                chain.proceed(request)
            }.build()

            // Re-create API with auth client
            // We need to cast or access the specific V0260 methods 
            // relying on MemosApiFactory to return the right implementation wrapping it
            val authApi = MemosApiFactory.create(baseUrl, authClient)

            val session = authApi.getCurrentSession()
            println("Session User: ${session.user}")
            assertEquals(TEST_USERNAME, session.user?.username)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun getAuthenticatedApi(): MemosApi {
        val logging = okhttp3.logging.HttpLoggingInterceptor { message ->
            println(message)
        }.apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Ensure we have an admin user (first user is admin)
        val signupJson = """
            {
                "username": "$TEST_USERNAME",
                "password": "$TEST_PASSWORD",
                "displayName": "$TEST_DISPLAY_NAME",
                "role": "HOST"
            }
        """.trimIndent()

        val signupRequest = Request.Builder()
            .url("${baseUrl}api/v1/users")
            .post(signupJson.toRequestBody("application/json".toMediaType()))
            .build()
        
        // Try to sign up, ignore failure if already exists (test re-run capability)
        try {
            client.newCall(signupRequest).execute().close()
        } catch (e: Exception) {
            // Ignore
        }

        val api = MemosApiFactory.create(baseUrl, client)
        
        // Login
        val token = loginAndCreateToken(api, baseUrl, TEST_USERNAME, TEST_PASSWORD)

        val authClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token").build()
                chain.proceed(request)
            }
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return MemosApiFactory.create(baseUrl, authClient)
    }

    @Test
    fun testMemoLifecycle() = runBlocking {
        println("Running testMemoLifecycle")
        val api = getAuthenticatedApi()

        // 1. Create Memos
        val memo1 = api.createMemo(org.example.memosm.model.Memo(content = "Memo 1"))
        println("Created Memo 1: ${memo1.name}")
        assertNotNull(memo1.name)
        assertEquals("Memo 1", memo1.content)

        val memo2 = api.createMemo(org.example.memosm.model.Memo(content = "Memo 2"))
        println("Created Memo 2: ${memo2.name}")
        assertNotNull(memo2.name)
        assertEquals("Memo 2", memo2.content)

        // 2. List Memos
        val listResponse = api.listMemos()
        println("List response: ${listResponse.memos?.size} memos")
        val memos = listResponse.memos ?: emptyList()
        assertTrue(memos.any { it.name == memo1.name })
        assertTrue(memos.any { it.name == memo2.name })

        // 3. Edit Memo 1
        // Note: UpdateMask is required for some APIs, usually field paths comma separated
        val updatedMemo1 = api.updateMemo(
            memo1.name!!,
            org.example.memosm.model.Memo(content = "Memo 1 Updated"),
            "content"
        )
        println("Updated Memo 1 content: ${updatedMemo1.content}")
        assertEquals("Memo 1 Updated", updatedMemo1.content)

        // 4. List to verify update
        val listResponse2 = api.listMemos()
        val memos2 = listResponse2.memos ?: emptyList()
        val fetchedMemo1 = memos2.find { it.name == memo1.name }
        assertNotNull(fetchedMemo1)
        assertEquals("Memo 1 Updated", fetchedMemo1?.content)

        // 5. Delete Memo 2
        api.deleteMemo(memo2.name!!)
        println("Deleted Memo 2: ${memo2.name}")

        // 6. Verify Deletion
        val listResponse3 = api.listMemos()
        val memos3 = listResponse3.memos ?: emptyList()
        assertTrue("Memo 2 should be deleted", memos3.none { it.name == memo2.name })
        assertTrue("Memo 1 should still exist", memos3.any { it.name == memo1.name })
    }
}
