package com.h14turkiye.lockedb.mongodb;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.h14turkiye.lockedb.ALock;
import com.h14turkiye.lockedb.ALockBuilder;
import com.h14turkiye.lockedb.LockFactory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;

public class MongoLockFactory implements LockFactory {
    private MongoCollection<Document> locksCollection;
    
    /**
     * Constructs a MongoLockFactory with the specified MongoDatabase.
     *
     * @param database the MongoDatabase instance to store lock information
     */
    public MongoLockFactory(MongoDatabase db) {
        locksCollection = db.listCollectionNames().into(new ArrayList<>()).contains("locks") ?
        db.getCollection("locks") : createLocksCollection(db);
    }
    
    // Builder pattern approach
    public ALockBuilder builder() {
        return new MongoLockBuilder(locksCollection);
    }
    
    // Direct creation approach
    public ALock createLock(String key) {
        return new MongoLock(locksCollection, key);
    }

     /**
     * Creates the "locks" collection in the MongoDB database if it does not exist.
     * The collection is configured with an expiration index on the "expires" field.
     *
     * @return the MongoCollection instance for locks
     */
    private MongoCollection<Document> createLocksCollection(MongoDatabase db) {
        db.createCollection("locks", new CreateCollectionOptions());
        MongoCollection<Document> collection = db.getCollection("locks");
        collection.createIndex(new Document("expires", 1), new IndexOptions().expireAfter(0L, TimeUnit.MILLISECONDS));

        return collection;
    }
}
