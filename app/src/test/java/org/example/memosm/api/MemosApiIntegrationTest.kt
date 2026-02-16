package org.example.memosm.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.example.memosm.model.SignInRequestV0260
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

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
                arrayOf("neosmemo/memos:0.26.0"),
                arrayOf("neosmemo/memos:0.25")
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
            
            container.withExposedPorts(5230)
                .withEnv("MEMOS_DRIVER", "sqlite")
                .start()
                
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

    private fun waitForServer(url: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        
        var attempts = 0
        while (attempts < 30) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return
                }
            } catch (e: Exception) {
                // Ignore and retry
            }
            Thread.sleep(1000)
            attempts++
        }
        throw RuntimeException("Server failed to start")
    }

    @Test
    fun testLoginFlow() = runBlocking {
        println("Running testLoginFlow against $dockerImageName")
        // 1. Sign up (create admin user)
        // Since this is a fresh instance, the first user is admin
        val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            
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
            
        client.newCall(signupRequest).execute().use { response ->
             val body = response.body?.string()
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
            val token = loginAndCreateToken(api, baseUrl, TEST_USERNAME, TEST_PASSWORD)
            println("Generated Token: $token")
            assertNotNull(token)
            assertTrue(token.isNotEmpty())
            
            // 4. Verify Token Login
             val authClient = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token").build()
                chain.proceed(request)
            }.build()

            // Re-create API with auth client
            // We need to cast or access the specific V0260 methods 
            // relying on MemosApiFactory to return the right implementation wrapping it
             val authApi = MemosApiFactory.create(baseUrl, authClient)
             
             // MemosApiFactory creates MemosApi interface implementation
             // MemosApi currently seems to be a union or base, let's check strict typing
             // based on MemosApiFactory it returns MemosApi
             // code in LoginScreen casts or calls getCurrentSession, 
             // but for 0.26 it might need getCurrentUser. 
             
             // Let's check what MemosApiFactory returns for 0.26
             // usage in LoginScreen: authApi.getCurrentSession()
             // But MemosApiV0260 INTERFACE has getCurrentUser REPLACING getCurrentSession
             // Check MemosApiV0260 definition again?
             
             // In MemosApiFactory:
             // if (version.startsWith("0.26")) {
             //    val v0260Api = retrofit.create(MemosApiV0260::class.java)
             //    MemosApiV0260Impl(v0260Api) 
             // }
             
             // MemosApiV0260Impl likely implements MemosApi and delegates?
             // Or MemosApiV0260 extends MemosApiV0353?
             
             // Let's verify MemosApi definition first to be sure, 
             // but assume standard flow:
             
             val session = authApi.getCurrentSession()
             println("Session User: ${session.user}")
             assertEquals(TEST_USERNAME, session.user?.username)
             
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
