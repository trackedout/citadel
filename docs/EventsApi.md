# EventsApi

All URIs are relative to *http://localhost:3000/v1*

Method | HTTP request | Description
------------- | ------------- | -------------
[**eventsGet**](EventsApi.md#eventsGet) | **GET** /events | Get all events
[**eventsPost**](EventsApi.md#eventsPost) | **POST** /events | Create an event


<a id="eventsGet"></a>
# **eventsGet**
> EventsGet200Response eventsGet(name, role, sortBy, projectBy, limit, page)

Get all events

Only admins can retrieve all events.

### Example
```kotlin
// Import classes:
//import org.trackedout.client.infrastructure.*
//import org.trackedout.client.models.*

val apiInstance = EventsApi()
val name : kotlin.String = name_example // kotlin.String | Event name
val role : kotlin.String = role_example // kotlin.String | Event role
val sortBy : kotlin.String = sortBy_example // kotlin.String | sort by query in the form of field:desc/asc (ex. name:asc)
val projectBy : kotlin.String = projectBy_example // kotlin.String | project by query in the form of field:hide/include (ex. name:hide)
val limit : kotlin.Int = 56 // kotlin.Int | Maximum number of events
val page : kotlin.Int = 56 // kotlin.Int | Page number
try {
    val result : EventsGet200Response = apiInstance.eventsGet(name, role, sortBy, projectBy, limit, page)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling EventsApi#eventsGet")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling EventsApi#eventsGet")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **name** | **kotlin.String**| Event name | [optional]
 **role** | **kotlin.String**| Event role | [optional]
 **sortBy** | **kotlin.String**| sort by query in the form of field:desc/asc (ex. name:asc) | [optional]
 **projectBy** | **kotlin.String**| project by query in the form of field:hide/include (ex. name:hide) | [optional]
 **limit** | **kotlin.Int**| Maximum number of events | [optional]
 **page** | **kotlin.Int**| Page number | [optional] [default to 1]

### Return type

[**EventsGet200Response**](EventsGet200Response.md)

### Authorization


Configure bearerAuth:
    ApiClient.accessToken = ""

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a id="eventsPost"></a>
# **eventsPost**
> Event eventsPost(eventsPostRequest)

Create an event

Log a dungeon event from one of the Decked Out 2 instances.

### Example
```kotlin
// Import classes:
//import org.trackedout.client.infrastructure.*
//import org.trackedout.client.models.*

val apiInstance = EventsApi()
val eventsPostRequest : EventsPostRequest =  // EventsPostRequest | 
try {
    val result : Event = apiInstance.eventsPost(eventsPostRequest)
    println(result)
} catch (e: ClientException) {
    println("4xx response calling EventsApi#eventsPost")
    e.printStackTrace()
} catch (e: ServerException) {
    println("5xx response calling EventsApi#eventsPost")
    e.printStackTrace()
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **eventsPostRequest** | [**EventsPostRequest**](EventsPostRequest.md)|  |

### Return type

[**Event**](Event.md)

### Authorization


Configure bearerAuth:
    ApiClient.accessToken = ""

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

