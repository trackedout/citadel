package org.trackedout.citadel.mongo

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase

object MongoDBManager {
    private lateinit var client: MongoClient

    fun initialize(connectionString: String) {
        client = MongoClients.create(connectionString)
    }

    fun getDatabase(databaseName: String): MongoDatabase = client.getDatabase(databaseName)

    fun shutdown() {
        if (::client.isInitialized) {
            client.close()
        }
    }
}
