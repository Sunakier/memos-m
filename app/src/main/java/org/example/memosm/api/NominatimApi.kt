package org.example.memosm.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NominatimApi {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2", // "jsonv2" is a good default for simple use
        @Header("User-Agent") userAgent: String = "MemosM/1.0"
    ): NominatimResponse
}

data class NominatimResponse(
    val place_id: Long,
    val licence: String,
    val osm_type: String,
    val osm_id: Long,
    val lat: String,
    val lon: String,
    val display_name: String,
    val address: Address?
)

data class Address(
    val road: String?,
    val village: String?,
    val county: String?,
    val state_district: String?,
    val state: String?,
    val iso3166_2_lvl4: String?,
    val postcode: String?,
    val country: String?,
    val country_code: String?
)
