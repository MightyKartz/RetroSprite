package com.retrosprite.app.data.db

import android.content.Context
import com.retrosprite.app.data.repository.DefaultGameRepository
import com.retrosprite.app.data.repository.DefaultKnowledgeRepository
import com.retrosprite.app.data.repository.DefaultRequestLogRepository
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.repository.RequestLogRepository

/**
 * Manual DI factory for the data layer.
 *
 * RetroSprite intentionally avoids Hilt/Dagger to keep the build graph
 * minimal during Phase 1. This object exposes lazily-built singletons
 * keyed off the application context, mirroring Hilt's `@Singleton` scope.
 */
object DatabaseModule {

    @Volatile private var dbRef: RetroSpriteDatabase? = null
    @Volatile private var requestLogRepoRef: RequestLogRepository? = null
    @Volatile private var gameRepoRef: GameRepository? = null
    @Volatile private var knowledgeRepoRef: KnowledgeRepository? = null

    fun provideDatabase(context: Context): RetroSpriteDatabase {
        return dbRef ?: synchronized(this) {
            dbRef ?: RetroSpriteDatabase.getInstance(context).also { dbRef = it }
        }
    }

    fun provideRequestLogRepository(context: Context): RequestLogRepository {
        return requestLogRepoRef ?: synchronized(this) {
            requestLogRepoRef ?: DefaultRequestLogRepository(
                provideDatabase(context).requestLogDao()
            ).also { requestLogRepoRef = it }
        }
    }

    fun provideGameRepository(context: Context): GameRepository {
        return gameRepoRef ?: synchronized(this) {
            gameRepoRef ?: DefaultGameRepository(
                provideDatabase(context).gameDao()
            ).also { gameRepoRef = it }
        }
    }

    fun provideKnowledgeRepository(context: Context): KnowledgeRepository {
        return knowledgeRepoRef ?: synchronized(this) {
            knowledgeRepoRef ?: run {
                val db = provideDatabase(context)
                DefaultKnowledgeRepository(
                    database = db,
                    dao = db.knowledgeDao(),
                    ftsDao = db.knowledgeFtsDao()
                )
            }.also { knowledgeRepoRef = it }
        }
    }

    /** Visible for tests. */
    internal fun resetForTests() {
        synchronized(this) {
            dbRef = null
            requestLogRepoRef = null
            gameRepoRef = null
            knowledgeRepoRef = null
        }
    }
}
