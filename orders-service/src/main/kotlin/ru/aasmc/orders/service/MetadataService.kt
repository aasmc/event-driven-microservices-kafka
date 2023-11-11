package ru.aasmc.orders.service

import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.streams.StreamsMetadata
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.stereotype.Service
import ru.aasmc.orders.error.NotFoundException
import ru.aasmc.orders.utils.HostStoreInfo

@Service
class MetadataService(
    private val factoryBean: StreamsBuilderFactoryBean
) {

    /**
     * Get the metadata for all of the instances of this Kafka Streams application
     * @return List of {@link HostStoreInfo}
     */
    fun streamsMetadata(): List<HostStoreInfo> {
        val metadata = factoryBean.kafkaStreams?.metadataForAllStreamsClients()
            ?: throw RuntimeException("Cannot obtain metadata for streams")
        return mapInstancesToHostStoreInfo(metadata)
    }

    /**
     * Get the metadata for all instances of this Kafka Streams application that currently
     * has the provided store.
     * @param store   The store to locate
     * @return  List of {@link HostStoreInfo}
     */
    fun streamsMetadataForStore(store: String): List<HostStoreInfo> {
        val metadata = factoryBean.kafkaStreams?.streamsMetadataForStore(store)
            ?: throw RuntimeException("Cannot obtain metadata for streams")
        return mapInstancesToHostStoreInfo(metadata)
    }

    /**
     * Find the metadata for the instance of this Kafka Streams Application that has the given
     * store and would have the given key if it exists.
     * @param store   Store to find
     * @param key     The key to find
     * @return {@link HostStoreInfo}
     */
    fun <K> streamsMetadataForStoreAndKey(
        store: String,
        key: K,
        serializer: Serializer<K>
    ): HostStoreInfo {
        // Get metadata for the instances of this Kafka Streams application hosting the store and
        // potentially the value for key
        val metadata = factoryBean.kafkaStreams?.queryMetadataForKey(store, key, serializer)
            ?: throw NotFoundException("Metadata for store $store not found!")

        return HostStoreInfo(
            host = metadata.activeHost().host(),
            port = metadata.activeHost().port(),
            storeNames = hashSetOf(store)
        )
    }

    private fun mapInstancesToHostStoreInfo(
        metadatas: Collection<StreamsMetadata>
    ): List<HostStoreInfo> {
        return metadatas.map { metadata ->
            HostStoreInfo(
                host = metadata.host(),
                port = metadata.port(),
                storeNames = metadata.stateStoreNames()
            )
        }
    }
}