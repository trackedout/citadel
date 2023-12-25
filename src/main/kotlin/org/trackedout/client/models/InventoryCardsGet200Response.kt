/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package org.trackedout.client.models

import org.trackedout.client.models.Card

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param results 
 * @param page 
 * @param limit 
 * @param totalPages 
 * @param totalResults 
 */


data class InventoryCardsGet200Response (

    @Json(name = "results")
    val results: kotlin.collections.List<Card>? = null,

    @Json(name = "page")
    val page: kotlin.Int? = null,

    @Json(name = "limit")
    val limit: kotlin.Int? = null,

    @Json(name = "totalPages")
    val totalPages: kotlin.Int? = null,

    @Json(name = "totalResults")
    val totalResults: kotlin.Int? = null

)

