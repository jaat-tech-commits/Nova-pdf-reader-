package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.DocumentRepository

class NovaPdfApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: DocumentRepository by lazy { DocumentRepository(database, applicationContext) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NovaPdfApplication
            private set
    }
}
