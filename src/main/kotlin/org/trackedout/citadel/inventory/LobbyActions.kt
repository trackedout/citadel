package org.trackedout.citadel.inventory

import org.trackedout.client.models.CatalogLobbyAction

// What the lobby does for each action a catalog can give a lobby item
interface LobbyActionHandler {
    fun openDeck()
    fun queue()
    fun showCollection(action: CatalogLobbyAction)
}

fun CatalogLobbyAction.dispatchTo(handler: LobbyActionHandler) = when (type) {
    CatalogLobbyAction.Type.openMinusDeck -> handler.openDeck()
    CatalogLobbyAction.Type.queue -> handler.queue()
    CatalogLobbyAction.Type.showMinusCollection -> handler.showCollection(this)
}
