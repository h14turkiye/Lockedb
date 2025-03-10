package com.h14turkiye.lockedb.mongodb;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MongoDBContainer;
import com.h14turkiye.lockedb.ALockTest;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoLockTest extends ALockTest {
    
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0");
    
    private static MongoClient client;
    private static MongoDatabase db;
    
    @BeforeAll
    static void setup() {
        try {
            mongoContainer.start();
            
            String uri = mongoContainer.getReplicaSetUrl(); // Dynamic MongoDB URI
            System.out.println(uri);
            
            client = MongoClients.create(uri);
            
            db = client.getDatabase("testdb");
            
            factory = new MongoLockFactory(db);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    @AfterAll
    static void tearDown() {
        mongoContainer.stop();
        client.close();
    }
}