package com.h14turkiye.mcschedlock.lock.platform.mongodb;

import java.util.Collections;

import org.bukkit.plugin.Plugin;

import com.h14turkiye.mcschedlock.lock.LockConnect;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoLockConnect {
    public static MongoDatabase database; // Database Instance

    /**
     * Connects to a MongoDB database, setting the database instance.
     * 
     * @param dbname The name of the database to connect to.
     * @param host The hostname of the MongoDB server.
     * @param port The port number of the MongoDB server.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @param dbauth The name of the database to authenticate against.
     * @param useSsl Whether to enable SSL/TLS encryption.
     */
    public static void connectToMongoDB(Plugin plugin, String dbname, String host, int port, String username, String password, String dbauth, boolean useSsl) {
        LockConnect.plugin = plugin;
        LockConnect.asyncLockType = MongoAsyncLock.class;
        
        // Create MongoDB credentials if username and password are provided
        MongoCredential credential = null;
        if (username != null && password != null) {
            credential = MongoCredential.createCredential(username, dbauth, password.toCharArray());
        }
        
        // Build the MongoClientSettings
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
        .applyToClusterSettings(builder ->
        builder.hosts(Collections.singletonList(new ServerAddress(host, port)))
        );
        
        // Apply credentials if they are available
        if (credential != null) {
            settingsBuilder.credential(credential);
        }
        
        // Enable SSL if requested
        if (useSsl) {
            settingsBuilder.applyToSslSettings(builder -> builder.enabled(true));
        }
        
        // Build the MongoClient
        MongoClientSettings settings = settingsBuilder.build();

        MongoClient mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(dbname);
        new MongoAsyncLock("startup");
    }

    /**
     * Connects to a MongoDB database instance for using with the {@link MongoAsyncLock} implementation.
     * 
     * @param plugin The plugin instance to associate with the lock provider.
     * @param database The MongoDB database instance to connect to.
     */
    public static void connectToMongoDB(Plugin plugin, MongoDatabase database) {
        LockConnect.plugin = plugin;
        LockConnect.asyncLockType = MongoAsyncLock.class;
        MongoLockConnect.database = database;
        new MongoAsyncLock("startup");
    }

    /**
     * Gets the database instance associated with this lock provider.
     * Must be cast to {@link com.mongodb.client.MongoDatabase MongoDatabase} etc.
     *
     * @return the database instance associated with this lock provider
     */
    public static Object getDatabase() {
        return database;
    }
}