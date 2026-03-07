---
name: Modify Room Database
description: Instructions for modifying the Room caching database in MemosM.
---

# Modify Room Database

MemosM uses Android Room for local caching.

## 1. Add/Modify Entity

Entities are located in `app/src/main/java/org/example/memosm/data/cache/`. Define your data class and annotate it with `@Entity`.

```kotlin
@Entity(tableName = "my_custom_table")
data class CachedData(
    @PrimaryKey val id: String,
    val content: String
)
```

## 2. Update DAO

Add query methods to `app/src/main/java/org/example/memosm/data/cache/MemoDao.kt` (or create a new DAO).

```kotlin
@Dao
interface MemoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(data: CachedData)
}
```

*Note: If you create a new DAO, expose it in `MemoCacheDatabase.kt` and provide it in Koin (`Modules.kt`).*

## 3. Database Migration

If you changed an existing entity or added a new one, you **must** increment the `version` in `MemoCacheDatabase.kt`.

```kotlin
@Database(
    entities = [CachedMemo::class, CachedData::class], 
    version = 2, // <-- Increment this
    exportSchema = false
)
abstract class MemoCacheDatabase : RoomDatabase() { ... }
```

In a production app, you should also provide a `Migration` block, but depending on the user requirements, a simple destructive migration (`fallbackToDestructiveMigration()`) might be used if it's just a pure cache. Check existing logic in `MemoCacheDatabase.kt` to follow the project's strategy.
