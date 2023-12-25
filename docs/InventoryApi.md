# InventoryApi

All URIs are relative to *http://localhost:3000/v1*

Method | HTTP request | Description
------------- | ------------- | -------------
[**inventoryAddCardPost**](InventoryApi.md#inventoryAddCardPost) | **POST** /inventory/add-card | Add a card to a player&#39;s deck
[**inventoryCardsGet**](InventoryApi.md#inventoryCardsGet) | **GET** /inventory/cards | Get all cards
[**inventoryDeleteCardPost**](InventoryApi.md#inventoryDeleteCardPost) | **POST** /inventory/delete-card | Delete a card


<a id="inventoryAddCardPost"></a>
# **inventoryAddCardPost**
> Card inventoryAddCardPost(card)

Add a card to a player&#39;s deck

Add a card to a player&#39;s deck from one of the Decked Out 2 instances or the lobby server.

### Example
```kotlin
// Import classes:
//import org.trackedout.client.infrastructure.*
//import org.trackedout.client.models.*

val apiInstance = InventoryApi()
val card : Card =  // Card | 
try {
    val result : Card = apiInstance.inventoryAddCardPost(card)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling InventoryApi#inventoryAddCardPost")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling InventoryApi#inventoryAddCardPost")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **card** | [**Card**](Card.md)|  |

### Return type

[**Card**](Card.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a id="inventoryCardsGet"></a>
# **inventoryCardsGet**
> InventoryCardsGet200Response inventoryCardsGet(name, player, deckId, sortBy, projectBy, limit, page)

Get all cards

Only admins can retrieve all cards.

### Example
```kotlin
// Import classes:
//import org.trackedout.client.infrastructure.*
//import org.trackedout.client.models.*

val apiInstance = InventoryApi()
val name : kotlin.String = name_example // kotlin.String | Card name
val player : kotlin.String = player_example // kotlin.String | Player
val deckId : kotlin.String = deckId_example // kotlin.String | Deck ID
val sortBy : kotlin.String = sortBy_example // kotlin.String | sort by query in the form of field:desc/asc (ex. name:asc)
val projectBy : kotlin.String = projectBy_example // kotlin.String | project by query in the form of field:hide/include (ex. name:hide)
val limit : kotlin.Int = 56 // kotlin.Int | Maximum number of cards
val page : kotlin.Int = 56 // kotlin.Int | Page number
try {
    val result : InventoryCardsGet200Response = apiInstance.inventoryCardsGet(name, player, deckId, sortBy, projectBy, limit, page)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling InventoryApi#inventoryCardsGet")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling InventoryApi#inventoryCardsGet")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **name** | **kotlin.String**| Card name | [optional]
 **player** | **kotlin.String**| Player | [optional]
 **deckId** | **kotlin.String**| Deck ID | [optional]
 **sortBy** | **kotlin.String**| sort by query in the form of field:desc/asc (ex. name:asc) | [optional]
 **projectBy** | **kotlin.String**| project by query in the form of field:hide/include (ex. name:hide) | [optional]
 **limit** | **kotlin.Int**| Maximum number of cards | [optional]
 **page** | **kotlin.Int**| Page number | [optional] [default to 1]

### Return type

[**InventoryCardsGet200Response**](InventoryCardsGet200Response.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="inventoryDeleteCardPost"></a>
# **inventoryDeleteCardPost**
> inventoryDeleteCardPost(card)

Delete a card

Remove a card from a player&#39;s deck. If multiple copies of this card exist, only one will be removed.

### Example
```kotlin
// Import classes:
//import org.trackedout.client.infrastructure.*
//import org.trackedout.client.models.*

val apiInstance = InventoryApi()
val card : Card =  // Card | 
try {
    apiInstance.inventoryDeleteCardPost(card)
} catch (e: ClientException) {
    println("4xx response calling InventoryApi#inventoryDeleteCardPost")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling InventoryApi#inventoryDeleteCardPost")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **card** | [**Card**](Card.md)|  |

### Return type

null (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

