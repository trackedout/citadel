package org.trackedout.citadel

import org.trackedout.citadel.config.CatalogData
import org.trackedout.client.infrastructure.Serializer
import org.trackedout.client.models.Catalog

// Catalog documents as `GET /v1/catalog/<slug>` serves them on burn, with most cards and artifacts left out
object CatalogFixture {
    const val COMPETITIVE = "do2-s2-comp"
    const val PRACTICE = "do2-s2-practice"
    const val METRO_MAYHEM = "metro-mayhem"

    fun document(slug: String): Catalog {
        val text = CatalogFixture::class.java.classLoader.getResourceAsStream("catalog/$slug.json")!!
            .bufferedReader().use { it.readText() }
        return Serializer.moshi.adapter(Catalog::class.java).fromJson(text)!!
    }

    fun load(slug: String): CatalogData = CatalogData(document(slug))
}
