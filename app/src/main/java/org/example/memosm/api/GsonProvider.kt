package org.example.memosm.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlin.time.Instant

/**
 * Shared Gson instance configured with type adapters for custom types
 * used across the app (Retrofit, Room cache, etc.).
 */
object GsonProvider {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()
}

/**
 * Gson TypeAdapter that serializes/deserializes [kotlin.time.Instant]
 * to/from ISO 8601 strings (e.g. "2026-02-02T21:50:22Z").
 */
class InstantTypeAdapter : TypeAdapter<Instant?>() {
    override fun write(out: JsonWriter, value: Instant?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(`in`: JsonReader): Instant? {
        if (`in`.peek() == JsonToken.NULL) {
            `in`.nextNull()
            return null
        }
        val str = `in`.nextString()
        return try {
            Instant.parse(str)
        } catch (_: Exception) {
            null
        }
    }
}
