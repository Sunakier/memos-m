package org.example.memosm

import org.junit.Test
import org.testcontainers.containers.GenericContainer


class ContainerTest {
    @Test
    fun testContainer() {
        // val socketPath = "/var/run/docker.sock"
        // Explicitly set the docker host to ensure Testcontainers finds it
        // val socketFile = java.io.File(socketPath)
        // if (socketFile.exists()) {
        //     System.setProperty("docker.host", "unix://$socketPath")
        // }

        println("test")
        GenericContainer("redis:5.0.3-alpine")
            .withExposedPorts(6379)
            .use { redis ->
                redis.start()
                assert(redis.isRunning)
            }
    }
}