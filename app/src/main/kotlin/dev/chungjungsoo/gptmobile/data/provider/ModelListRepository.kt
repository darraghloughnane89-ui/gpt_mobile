package dev.chungjungsoo.gptmobile.data.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelListRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    networkClient: NetworkClient
) {
    private val cache = ModelListCache(File(context.cacheDir, "model-lists"))
    private val client = ModelListClient(networkClient())

    fun cached(clientType: ClientType, apiUrl: String): List<RemoteModel> {
        val stored = cache.read(clientType.name, apiUrl)?.models.orEmpty()
        return stored.ifEmpty { defaults(clientType) }
    }

    fun defaults(clientType: ClientType): List<RemoteModel> = ModelConstants.builtinModelIds(clientType).map { id ->
        RemoteModel(id = id, supportsVision = id.contains("vision", ignoreCase = true))
    }

    suspend fun refresh(clientType: ClientType, apiUrl: String, token: String?): List<RemoteModel> {
        val models = client.fetch(clientType, apiUrl, token)
        if (models.isNotEmpty()) {
            cache.write(
                clientType.name,
                apiUrl,
                CachedModelList(fetchedAtEpochMs = System.currentTimeMillis(), models = models)
            )
        }
        return models.ifEmpty { defaults(clientType) }
    }

    fun endpoint(clientType: ClientType, apiUrl: String): String = client.endpoint(clientType, apiUrl)
}
