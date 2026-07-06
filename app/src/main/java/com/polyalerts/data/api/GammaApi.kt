package com.polyalerts.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Public, read-only Polymarket data API. No auth required. */
interface GammaApi {

    /** Browse/search active markets. `query` does a server-side text search. */
    @GET("markets")
    suspend fun markets(
        @Query("active") active: Boolean = true,
        @Query("closed") closed: Boolean = false,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "volume24hr",
        @Query("ascending") ascending: Boolean = false,
        @Query("tag_id") tagId: Int? = null,   // null = all categories
    ): List<Market>

    /** Fetch a single market by id — used by the alert worker to get the latest price. */
    @GET("markets/{id}")
    suspend fun market(@Path("id") id: String): Market

    /** Server-side full-text search across all markets (returns events with nested markets). */
    @GET("public-search")
    suspend fun search(
        @Query("q") q: String,
        @Query("limit_per_type") limitPerType: Int = 40,
    ): SearchResponse
}
