---
name: Add Memos API Endpoint
description: Instructions for adding a new API endpoint handling versioning backward compatibility in MemosM.
---

# Add Memos API Endpoint

When adding a new API endpoint to the MemosM Android client, you must handle the API versioning correctly.

## 1. Add to the Base Interface

Add the abstract method definition to `app/src/main/java/org/example/memosm/api/MemosApi.kt`.
This interface acts as the central contract for the app. The methods here should **not** have Retrofit annotations (like `@GET` or `@POST`).

```kotlin
interface MemosApi {
    // ...
    suspend fun getMyNewData(param1: String): MyNewDataResponse
}
```

## 2. Add to the Retrofit Interfaces

The network requests are executed via Retrofit in the versioned interfaces. The core one is `MemosApiV0353.kt` (or the latest version file).
Add the Retrofit annotated method there:

```kotlin
interface MemosApiV0353 {
    @GET("api/v1/mynewdata")
    suspend fun getMyNewData(@Query("param1") param1: String): MyNewDataResponse
}
```

If the endpoint has a different signature in older API versions, you might need to add/override it in `MemosApiV0260.kt` or `MemosApiV0270.kt`.

## 3. Implement in the Impl Classes

Each version has an implementation class (e.g., `MemosApiV0353Impl.kt`) that implements `MemosApi` and delegates to the Retrofit interface.

```kotlin
class MemosApiV0353Impl(private val api: MemosApiV0353) : MemosApi {
    // ...
    override suspend fun getMyNewData(param1: String): MyNewDataResponse {
        return api.getMyNewData(param1)
    }
}
```

Make sure to implement the method in the older version implementations (`MemosApiV0260Impl.kt`, etc.) as well, either delegating to the network call or returning a fallback/throwing an exception if the API is unsupported in that old version.
