package com.cyberpulse.app

import android.content.Context
import com.cyberpulse.app.data.NewsRepository
import com.cyberpulse.app.data.Settings
import com.cyberpulse.app.data.db.AppDatabase

/** Hand-rolled dependency container; one instance lives on [CyberPulseApplication]. */
class AppGraph(context: Context) {
    val settings = Settings(context)
    val database = AppDatabase.create(context)
    val repository = NewsRepository(database.articleDao(), settings)
}

val Context.appGraph: AppGraph
    get() = (applicationContext as CyberPulseApplication).graph
