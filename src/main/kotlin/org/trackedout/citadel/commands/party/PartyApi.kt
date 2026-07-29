package org.trackedout.citadel.commands.party

import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import org.trackedout.client.infrastructure.ApiClient
import org.trackedout.client.infrastructure.ApiResponse
import org.trackedout.client.infrastructure.ClientError
import org.trackedout.client.infrastructure.ClientException
import org.trackedout.client.infrastructure.MultiValueMap
import org.trackedout.client.infrastructure.RequestConfig
import org.trackedout.client.infrastructure.RequestMethod
import org.trackedout.client.infrastructure.ResponseType
import org.trackedout.client.infrastructure.ServerError
import org.trackedout.client.infrastructure.ServerException
import org.trackedout.client.infrastructure.Success
import java.io.IOException

class PartyApi(basePath: String = defaultBasePath, client: OkHttpClient = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.baseUrlKey, "http://localhost:3000/v1")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun getGameRegistry(): GameRegistryResponse {
        val localVariableConfig = RequestConfig<Unit>(
            method = RequestMethod.GET,
            path = "/game-registry",
            query = mutableMapOf(),
            headers = mutableMapOf("Accept" to "application/json"),
            requiresAuthentication = false,
            body = null,
        )

        val localVarResponse = request<Unit, GameRegistryResponse>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun createParty(leader: String, game: String): Party {
        val body = CreatePartyRequest(leader = leader, game = game)
        val localVariableConfig = RequestConfig<CreatePartyRequest>(
            method = RequestMethod.POST,
            path = "/parties",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<CreatePartyRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun getParty(player: String): Party {
        val localVariableConfig = RequestConfig<Unit>(
            method = RequestMethod.GET,
            path = "/parties",
            query = mutableMapOf("player" to listOf(player)),
            headers = mutableMapOf("Accept" to "application/json"),
            requiresAuthentication = false,
            body = null,
        )

        val localVarResponse = request<Unit, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun invitePlayer(partyId: String, player: String): Party {
        val body = PartyPlayerRequest(player = player)
        val localVariableConfig = RequestConfig<PartyPlayerRequest>(
            method = RequestMethod.POST,
            path = "/parties/${encodeURIComponent(partyId)}/invite",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<PartyPlayerRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun joinParty(partyId: String, player: String): Party {
        val body = PartyPlayerRequest(player = player)
        val localVariableConfig = RequestConfig<PartyPlayerRequest>(
            method = RequestMethod.POST,
            path = "/parties/${encodeURIComponent(partyId)}/join",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<PartyPlayerRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun leaveParty(partyId: String, player: String): Party {
        val body = PartyPlayerRequest(player = player)
        val localVariableConfig = RequestConfig<PartyPlayerRequest>(
            method = RequestMethod.POST,
            path = "/parties/${encodeURIComponent(partyId)}/leave",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<PartyPlayerRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun kickPlayer(partyId: String, player: String): Party {
        val body = PartyPlayerRequest(player = player)
        val localVariableConfig = RequestConfig<PartyPlayerRequest>(
            method = RequestMethod.POST,
            path = "/parties/${encodeURIComponent(partyId)}/kick",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<PartyPlayerRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun queueParty(partyId: String, player: String): Party {
        val body = PartyPlayerRequest(player = player)
        val localVariableConfig = RequestConfig<PartyPlayerRequest>(
            method = RequestMethod.POST,
            path = "/parties/${encodeURIComponent(partyId)}/queue",
            query = mutableMapOf(),
            headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            requiresAuthentication = false,
            body = body,
        )

        val localVarResponse = request<PartyPlayerRequest, Party>(localVariableConfig)
        return handleResponse(localVarResponse)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, ClientException::class, ServerException::class)
    fun disbandParty(partyId: String): Unit {
        val localVariableConfig = RequestConfig<Unit>(
            method = RequestMethod.DELETE,
            path = "/parties/${encodeURIComponent(partyId)}",
            query = mutableMapOf(),
            headers = mutableMapOf("Accept" to "application/json"),
            requiresAuthentication = false,
            body = null,
        )

        val localVarResponse = request<Unit, Unit>(localVariableConfig)
        when (localVarResponse.responseType) {
            ResponseType.Success -> return
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
            else -> throw UnsupportedOperationException("Unexpected response type: ${localVarResponse.responseType}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> handleResponse(response: ApiResponse<T?>): T {
        return when (response.responseType) {
            ResponseType.Success -> (response as Success<*>).data as T
            ResponseType.ClientError -> {
                val error = response as ClientError<*>
                throw ClientException("Client error : ${error.statusCode} ${error.message.orEmpty()}", error.statusCode, response)
            }
            ResponseType.ServerError -> {
                val error = response as ServerError<*>
                throw ServerException("Server error : ${error.statusCode} ${error.message.orEmpty()} ${error.body}", error.statusCode, response)
            }
            else -> throw UnsupportedOperationException("Unexpected response type: ${response.responseType}")
        }
    }

    private fun encodeURIComponent(uriComponent: String): String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
