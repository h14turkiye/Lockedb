package com.h14turkiye.lockedb.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.h14turkiye.lockedb.ALockTest;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

public class RedisLockTest extends ALockTest {
    // Using TestContainers' GenericContainer for Redis
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:6.2.6")).withExposedPorts(6379);
    
    private static RedisClient redisClient;
    
    @BeforeAll
    static void setup() {
        try {
            // Start the Redis container
            redisContainer.start();
            
            // Get the dynamic port and host for the Redis container
            String host = redisContainer.getHost();
            int port = redisContainer.getMappedPort(6379);
            
            System.out.println("Redis available at " + host + ":" + port);
            
            // Create Redis client with the container connection details
            RedisURI redisUri = RedisURI.create(host, port);
            redisClient = RedisClient.create(redisUri);
            
            // Create and assign the lock factory
            factory = new RedisLockFactory(redisClient);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @AfterAll
    static void tearDown() {
        // Close Redis client
        if (redisClient != null) {
            redisClient.shutdown();
        }
        
        // Stop the container
        redisContainer.stop();
    }
}