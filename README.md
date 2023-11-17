# Event Driven Microservices With Kafka Streams and ksqlDB
Демо проект по микросервисной архитектуре на основе событий.
Основан на статьях Confluent: 

1. [Tutorial: Introduction to Streaming Application Development](https://docs.confluent.io/platform/current/tutorials/examples/microservices-orders/docs/index.html)

2. [Building a Microservices Ecosystem with Kafka Streams and KSQL](https://www.confluent.io/blog/building-a-microservices-ecosystem-with-kafka-streams-and-ksql/)

### Стек Технологий
1. Kotlin
2. Spring Boot
3. Apache Kafka
4. Kafka Streams
5. ksqlDB
6. Gradle
7. Docker / docker-compose
8. Avro Serialization
9. Confluent Schema Registry
10. Kafka Connect
11. ElasticSearch
12. SQLite

### Верхнеуровневое описание архитектуры

![architecture_overview.png](art%2Farchitecture_overview.png)

Центром системы микросервисов является Orders Service, который предоставляет клиентам возможность
посылать REST запросы на:
- соднание заказов (POST http://localhost:8000/v1/orders)
- получение заказа по его ID (GET http://localhost:8000/v1/orders/{id})
- получение валидированного заказа по его ID (GET http://localhost:8000/v1/orders/{id}/validated)

При создании заказа в Kafka топик `orders.v1` посылается соответствующее событие, оно вычитывается
различными сервисами, отвечающими за валидацию заказа: Inventory Service, Fraud Service и Order Details Service.
Эти сервисы параллельно и независимо друг от друга осуществляют валидацию заказа, а результат
PASS или FAIL записывают в топик Kafka order-validations.v1. Validation Aggregator Service 
вычитывает этот топик, аггрегирует результаты по каждому заказу и отправляет конечный результат
в топик `orders.v1`. 

Для реализации GET запросов Orders Service использует материализованное представление 
(materialized view), которое встроено (embedded) в каждый инстанс Orders Service. Это 
материализованное представление реализовано на основе Kafka Streams queryable state store. 
Так как state store основан на топиках Kafka, и встроен в каждый инстанс, он хранит данные
только тех партиций топика, которые вычитываются данным инстансом. Однако Kafka Streams 
предоставляет API (Interactive Queries) для того, чтобы "найти" инстанс, на котором хранятся данные, если их 
нет локально. Это позволяет реализовать гарантию для клиентов read-your-own-writes (чтение своих записей).

Также в системе есть простой сервис для отправки сообщений на почту Email Service (в текущей 
реализации просто осуществляется логирование отправляемых сообщений). 

Сервис Order Enrichment отвечает за создание orders_enriched_stream в ksqlDB, из которого
можно получить данные о покупателе и его заказе. Также этот сервис предоставляет REST ручку
для получения информации о фозможных мошеннических действиях (если покупатель осуществил более 2
заказов за 30 секунд):
- GET http://localhost:8010/fraud

Данные о покупателях вычитываются из БД SQLite с помощью Kafka Connect `io.confluent.connect.jdbc.JdbcSourceConnector`
в топик Kafka customers.
Конфигурация коннектора: [connector_jdbc_customers_template.config](infra%2Fconnectors%2Fconnector_jdbc_customers_template.config)

Данные о заказах записываются из топика Kafka `orders.v1` в индекс `orders.v1` ElasticSearch
с помощью Kafka Connect `io.confluent.connect.elasticsearch.ElasticsearchSinkConnector`.
Конфигурация коннектора: [connector_elasticsearch_template.config](infra%2Fconnectors%2Fconnector_elasticsearch_template.config)

![elastic-search-kafka.png](art%2Felastic-search-kafka.png)


#### Диаграмма микросервисов и топиков Kafka:
![microservices-diagram.png](art%2Fmicroservices-diagram.png)

Все микросервисы написаны на Kotlin с использованием Kafka Streams и Spring for Apache Kafka.

## Детали реализации

### Common
Данный модуль собран как библиотека, которую используют микросервисы. Содержит сгенерированный AVRO классы
а также утиллитные классы, облегчающие работу с топиками, сериализаторами и десериализаторами. 
#### Сущности
Генерация сущностей, с которыми работает Kafka осуществляется [на основе схем Avro]([avro](common%2Fsrc%2Fmain%2Favro)) с помощью
Gradle plugin `id 'com.github.davidmc24.gradle.plugin.avro' version "1.9.1"`, на этапе сборки
библиотеки common. Ниже приведено описание сущностей без дополнительного сгенерированного кода.
```kotlin
class Customer(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val address: String,
    val level: String = "bronze" // possible values: platinum, gold, silver, bronze
)

enum class OrderState {
    CREATED, VALIDATED, FAILED, SHIPPED
}

enum class Product {
    JUMPERS, UNDERPANTS, STOCKINGS
}

class Order(
    val id: String,
    val customerId: Long,
    val state: OrderState,
    val product: Product,
    val quantity: Int,
    val price: Double
)

class OrderValue(
    val order: Order,
    val value: Double
)

class OrderEnriched(
    val id: String,
    val customerId: Long,
    val customerLevel: String
)

enum class OrderValidationType {
   INVENTORY_CHECK, FRAUD_CHECK, ORDER_DETAILS_CHECK
}

enum class OrderValidationResult {
   PASS, FAIL, ERROR
}

class OrderValidation(
    val orderId: String,
    val checkType: OrderValidationType,
    val validationResult: OrderValidationResult
)

class Payment(
    val id: String,
    val orderId: String,
    val ccy: String,
    val amount: Double
)
```

Регистрация схем Avro в Confluent Schema Registry осуществляется после того, как поднят docker 
контейнер schema-registry с помощью Gradle Plugin: `id 'com.github.imflog.kafka-schema-registry-gradle-plugin' version "1.12.0" `.
Чтобы зарегистрировать схемы необходимо настроить плагин. Минимальная настройка:
```groovy
schemaRegistry {
    url = 'http://localhost:8081'
    quiet = true
    register {
        subject('customers-value', 'common/src/main/avro/customer.avsc', 'AVRO')
        subject('orders.v1-value', 'common/src/main/avro/order.avsc', 'AVRO')
        subject('orders-enriched.v1-value', 'common/src/main/avro/orderenriched.avsc', 'AVRO')
        subject('order-validations.v1-value', 'common/src/main/avro/ordervalidation.avsc', 'AVRO')
        subject('payments.v1-value', 'common/src/main/avro/payment.avsc', 'AVRO')
    }
}
```

При регистрации указываются:
1. Название схемы. Одним из паттернов наменования является TopicName-value / TopicName-key
2. Путь до файла со схемой
3. Формат сериализации, в нашем случае - AVRO.

Далее необходимо запустить таску Gradle из корневой директории:
```shell
./gradlew registerSchemaTask
```

Для работы с записями в топиках приложение Kafka Streams должно знать о формате сериализации данных.
Для этого настраиваются специальные `org.apache.kafka.common.serialization.Serde` классы, которые знают
как сериализовать/десериализовать ключ/значение.
Для удобства все настройки помещены в отдельный класс [Schemas]([Schemas.kt](common%2Fsrc%2Fmain%2Fkotlin%2Fru%2Faasmc%2Feventdriven%2Fcommon%2Fschemas%2FSchemas.kt)). 
Для сгенерированных AVRO классов используется `io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde`,
при настройке которого указывается адрес Schema Registry.

```kotlin
fun configureSerdes() {
    val config = hashMapOf<String, Any>(
       AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicsProps.schemaRegistryUrl
    )
   for (topic in ALL.values) {
       configureSerde(topic.keySerde, config, true)
      configureSerde(topic.valueSerde, config, false)
   }
   configureSerde(ORDER_VALUE_SERDE, config, false)
}

fun configureSerde(serde: Serde<*>, config: Map<String, Any>, isKey: Boolean) {
    if (serde is SpecificAvroSerde) {
        serde.configure(config, isKey)
    }
}
```

## Orders Service

Зависимости:
```groovy
dependencies {
    implementation project(':common')
    implementation 'org.apache.kafka:kafka-streams'
    implementation 'org.jetbrains.kotlin:kotlin-reflect'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation("io.confluent:kafka-avro-serializer:7.5.2")
    implementation("io.confluent:kafka-streams-avro-serde:7.5.2")
    implementation("org.apache.avro:avro:1.11.0")
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:kafka'
}
```

Необходимо обращать внимание на соответствие версий `io.confluent:kafka-avro-serializer:7.5.2` 
и `io.confluent:kafka-streams-avro-serde:7.5.2`. 

В Spring Boot нет необходимости вручную управлять жизненным циклом сущности KafkaStreams.
Для этого есть абстракция `org.springframework.kafka.config.StreamsBuilderFactoryBean`, 
которая создает и управляет `org.apache.kafka.streams.KafkaStreams` и `org.apache.kafka.streams.StreamsBuilder`.
Тем не менее, нам необходимо сконфигурировать бин `org.springframework.kafka.config.KafkaStreamsConfiguration`:

```kotlin
@EnableKafkaStreams
@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps,
    private val serviceUtils: ServiceUtils,
    private val topicProps: TopicsProps
) {

    @Bean
    fun outstandingRequests(): MutableMap<String, FilteredResponse<String, Order, OrderDto>> {
        return ConcurrentHashMap()
    }

    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun kStreamsConfig(): KafkaStreamsConfiguration {
        val props = hashMapOf<String, Any>(
            StreamsConfig.APPLICATION_ID_CONFIG to kafkaProps.appId,
            // must be specified to enable InteractiveQueries and checking metadata of Kafka Cluster
            StreamsConfig.APPLICATION_SERVER_CONFIG to serviceUtils.getServerAddress(),
            StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to Serdes.String().javaClass.name,
            // instances MUST have different stateDir
            StreamsConfig.STATE_DIR_CONFIG to kafkaProps.stateDir,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaProps.autoOffsetReset,
            StreamsConfig.PROCESSING_GUARANTEE_CONFIG to kafkaProps.processingGuarantee,
            StreamsConfig.COMMIT_INTERVAL_MS_CONFIG to kafkaProps.commitInterval,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicProps.schemaRegistryUrl,
            StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG) to kafkaProps.sessionTimeout
        )
        return KafkaStreamsConfiguration(props)
    }
}
```

Для того, чтобы работали Interactive Queries (если мы хотим запрашивать данные не только 
из локального state store, но и с других инстансов), необходимо в конфигурациях указать
`StreamsConfig.APPLICATION_SERVER_CONFIG` - адрес текущего инстанса в формате `host:port`. 
Также мы указываем адрес Schema Registry, путь до каталога, где будет хранится state store и т.д.
По умолчанию Kafka Streams использует встроенное key-value хранилище RocksDB в качестве state store.

При старте приложения Orders Service на основе топика Kafka `orders.v1` создается KTable - абстракция Kafka Streams,
которая представляет из себя snapshot состояния (можно провести аналогию с compacted topic) - хранятся
последние записи по ключу в топике. При поступлении новых записей данные в KTable обновляются, фактически
производится операция `UPSERT` - если ключ есть в таблице, данные обновляются, если нет - создаются,
если приходит ключ - `null` - то это означает удаление данных, более они не будут использоваться 
при выборке по ключу или соединении таблицы с другими сущностями KafkaStreams (KStream, GlobalKTable).
При этом Kafka Streams гарантирует, что в случае падения инстанса, данные не будут потеряны,
так как они помимо локального state store, хранятся в change log топике Kafka -для сокращения 
потребления ресурсов по памяти этот топик - compacted.

Для того, чтобы приложение имело возможность получать данные из state store, необходимо
его материализовать, указав название, по которому в дальнейшем к нему можно обращатсья.

```kotlin
@Component
class OrdersServiceTopology(
    private val orderProps: OrdersProps,
    private val mapper: OrderMapper,
    private val schemas: Schemas,
    private val outstandingRequests: MutableMap<String, FilteredResponse<String, Order, OrderDto>>
) {

    @Autowired
    fun ordersTableTopology(builder: StreamsBuilder) {
        log.info("Calling OrdersServiceTopology.ordersTableTopology()")
        builder.table(
            orderProps.topic,
            Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde),
            Materialized.`as`(orderProps.storeName)
        ).toStream().foreach(::maybeCompleteLongPollGet)
    }

    private fun maybeCompleteLongPollGet(id: String, order: Order) {
        val callback = outstandingRequests[id]
        if (callback?.asyncResponse?.isSetOrExpired == true) {
            outstandingRequests.remove(id)
        } else if (callback != null && callback.predicate(id, order)) {
            callback.asyncResponse.setResult(mapper.toDto(order))
           outstandingRequests.remove(id)
        }
    }
}
```

Метод `OrdersServiceTopology.maybeCompleteLongPollGet(String, Order)` получает приходящие в 
KTable данные о заказах, и если новый заказ хранится в ConcurrentHashMap, и он удовлетворяет условию
FilteredResponse.predicate, то тогда мы комплитим DeferredResult и удаляем его из ConcurrentHashMap.

Класс `org.springframework.web.context.request.async.DeferredResult` позволяет асинхронно
обработать REST запрос. В Orders Service он используется следующим образом: в качестве 
возвращаемого значения в контроллере используется DeferredResult. Поток, принявший запрос
можно освободить, а проставить значение в DeferredResult можно из другого потока. При этом
можно указать таймаут, после которого DeferredResult вернет ошибку. При получении заказа - `getOrder(id)` -
если ни в локальном state store, ни в state store на других инстансах нет заказа с нужным
ID, мы помещаем DeferredResult в ConcurrentHashMap. 

Пример метода в контроллере:
```kotlin
@GetMapping("/{id}")
fun getOrder(
   @PathVariable("id") id: String,
   @RequestParam("timeout", defaultValue = "2000") timeout: Long
): DeferredResult<OrderDto> { 
   log.info("Received request to GET order with id = {}", id)
   val deferredResult = DeferredResult<OrderDto>(timeout)
   ordersService.getOrderDto(id, deferredResult, timeout)
   return deferredResult
}
```

Пример работы с DeferredResult:
```kotlin
   fun getOrderDto(id: String, asyncResponse: DeferredResult<OrderDto>, timeout: Long) {
        initOrderStore()
        CompletableFuture.runAsync {
            val hostForKey = getKeyLocationOrBlock(id, asyncResponse)
                ?: return@runAsync //request timed out so return

            if (thisHost(hostForKey)) {
                fetchLocal(id, asyncResponse) { _, _ -> true }
            } else {
                val path = Paths(hostForKey.host, hostForKey.port).urlGet(id)
                fetchFromOtherHost(path, asyncResponse, timeout)
            }
        }
    }

    private fun fetchLocal(
       id: String,
       asyncResponse: DeferredResult<OrderDto>,
       predicate: (String, Order) -> Boolean
    ) {
       log.info("running GET on this node")
       try {
          val order = orderStore.get(id)
          if (order == null || !predicate(id, order)) {
             log.info("Delaying get as order not present for id $id")
             outstandingRequests[id] = FilteredResponse(
                asyncResponse = asyncResponse,
                predicate = predicate
             )
          } else {
             asyncResponse.setResult(mapper.toDto(order))
          }
       } catch (e: InvalidStateStoreException) {
          log.error("Exception while querying local state store. {}", e.message)
          outstandingRequests[id] = FilteredResponse(asyncResponse, predicate)
       }
    }
    
    private fun fetchFromOtherHost(
       path: String,
       asyncResponse: DeferredResult<OrderDto>,
       timeout: Long
    ) {
       log.info("Chaining GET to a different instance: {}", path)
       try {
          val order = webClientBuilder.build()
             .get()
             .uri("$path?timeout=$timeout")
             .retrieve()
             .bodyToMono<OrderDto>()
             .block() ?: throw NotFoundException("FetchFromOtherHost returned null for request: $path")
          asyncResponse.setResult(order)
       } catch (swallowed: Exception) {
          log.warn("FetchFromOtherHost failed.", swallowed)
       }
    }
```

Для того, чтобы обратиться к state store, нам необходимо запросить его из KafkaStreams. Тут стоит
обратить внимание на то, что нам предоставляется только read-only хранилище. 

```kotlin
private lateinit var orderStore: ReadOnlyKeyValueStore<String, Order>

fun initOrderStore() {
    if (!::orderStore.isInitialized) {
        val streams = factoryBean.kafkaStreams
           ?: throw RuntimeException("Cannot obtain KafkaStreams instance.")
       orderStore = streams.store(
          StoreQueryParameters.fromNameAndType(
             ordersProps.storeName,
             QueryableStoreTypes.keyValueStore()
          )
       )
    }
}
```

Для получения информации о том, на каком инстансе хранятся данные о заказе с конкретным ID, 
Kafka Streams предоставляет API:
```kotlin
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
```

При создании заказа, Orders Service отправляет сообщение в топик Kafka `orders.v1` с помощью
`KafkaTemplate<String, Order>`. Настройки KafkaProducer в данном случае указываются в
application.yml файле:
```yml
spring:
  application:
    name: order-service
  kafka:
    bootstrap-servers: ${kafkaprops.bootstrapServers}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      bootstrap-servers: ${kafkaprops.bootstrapServers}
      acks: ${kafkaprops.acks}
      client-id: ${kafkaprops.clientId}
      properties:
        enable.idempotence: ${kafkaprops.enableIdempotence}
        schema.registry.url: ${kafkaprops.schemaRegistryUrl}
        auto.register.schemas: false
        use.latest.version: true
```

Особое внимание стоит обратить на настройки `auto.register.schema: false` и `use.latest.version: true`.
По умолчанию они выставлены наоборот. И если оставить их без изменения, то могут возникнуть
ошибки при сериализации / десериализации данных из-за возможной невосместимости схем AVRO. 
Confluent рекомендует в проде не предоставлять возможность продъюсерам автоматичски регистрировать
схему. 

## Order Details Service
[Сервис отвечает за проверку деталей заказа]([KafkaOrderDetailsService.kt](order-details-service%2Fsrc%2Fmain%2Fkotlin%2Fru%2Faasmc%2Forderdetails%2Fservice%2FKafkaOrderDetailsService.kt)):
- количество товаров не меньше 0
- стоимость заказа не меньше 0
- товар присутствует в заказе

Сообщения вычитываются из топика Kafka `orders.v1`, заказ проверяется, а результат проверки
отправляется в топик Kafka `order-validations.v1`. В этом сервисе не используется Kafka Streams,
а используются стандартные Kafka Producer Consumer.

Конфигурация Kafka Consumer:
```kotlin
    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Order>
    ): ConcurrentKafkaListenerContainerFactory<String, Order> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Order>()
        factory.consumerFactory = consumerFactory
        return factory
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Order> {
        return DefaultKafkaConsumerFactory(consumerProps())
    }

    private fun consumerProps(): Map<String, Any> {
        val props = hashMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to kafkaProps.appId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaProps.autoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to !kafkaProps.enableExactlyOnce,
            ConsumerConfig.CLIENT_ID_CONFIG to kafkaProps.appId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to schemas.ORDERS.keySerde.deserializer()::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to schemas.ORDERS.valueSerde.deserializer()::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl,
            "specific.avro.reader" to true
        )
        return props
    }
```

Здесь важно обратить внимание на конфигурацию `specific.avro.reader: true`. Она сообщает
консъюмеру, что он вычитывает не обобщенный AVRO тип записи (GenericAvroRecord), а конкретный тип
SpecificAvroRecord, и имеет возможность получать доступ к полям и методам без необходимости кастить
к конкретному типу.

Конфигурация Kafka Producer:
```kotlin
    @Bean
    fun producerFactory(): ProducerFactory<String, OrderValidation> {
        return DefaultKafkaProducerFactory(senderProps())
    }

    private fun senderProps(): Map<String, Any> {
        val props = hashMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to kafkaProps.enableIdempotence,
            ProducerConfig.RETRIES_CONFIG to Int.MAX_VALUE.toString(),
            ProducerConfig.ACKS_CONFIG to kafkaProps.acks,
            ProducerConfig.CLIENT_ID_CONFIG to kafkaProps.appId,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to schemas.ORDER_VALIDATIONS.valueSerde.serializer().javaClass.name,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to schemas.ORDER_VALIDATIONS.keySerde.serializer().javaClass.name,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION to true,
        )
        if (kafkaProps.enableExactlyOnce) {
            // attempt to provide a unique transactional ID which is a recommended practice
            props[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "${kafkaProps.appId}-${applicationProps.port}"
        }
        return props
    }

    @Bean
    fun kafkaTemplate(
        producerFactory: ProducerFactory<String, OrderValidation>
    ): KafkaTemplate<String, OrderValidation> {
        return KafkaTemplate(producerFactory)
    }
```
Тут стоит обратить внимание на две вещи. Первая, как уже было сказано выше - мы запрещаем продъюсеру
автоматически регистрировать схемы в Schema Registry, а также указываем использовать последнюю
версию схемы при сериализации данных. Второй момент касается настройки `ProducerConfig.TRANSACTIONAL_ID_CONFIG`,
эта настройка должна быть уникальной для каждого инстанса приложения, чтобы Kafka могла гарантировать
корректную работу с транзакциями в случае падения приложения. При этом важно понимать, что
уникальный - не значит рандомный. То есть, за инстансом должен быть закреплен свой `TRANSACTIONAL_ID`.
В данном случае для простоты реализации я использую суффикс в виде порта, на котором поднято 
приложение. Однако, эта лишь демо реализация. 

## [Email Service]([EmailService.kt](email-service%2Fsrc%2Fmain%2Fkotlin%2Fru%2Faasmc%2Femail%2Fservice%2FEmailService.kt)) 

![email-service-join.png](art%2Femail-service-join.png)

Сервис вычитывает данные из нескольких топиков Kafka: `customers`, `payments.v1`, `orders.v1`,
объединяет их с помощью Kafka Streams, и отправляет сообщение через интерфейс [Emailer]([Emailer.kt](email-service%2Fsrc%2Fmain%2Fkotlin%2Fru%2Faasmc%2Femail%2Fservice%2FEmailer.kt)).
Кроме того, данные о заказе и покупателе отправляются в топик Kafka, совпадающий с рейтингом
покупателя: `platinum`, `gold`, `silver`, `bronze`. 

![microservices-exercise-3.png](art%2Fmicroservices-exercise-3.png)
Настройки Kafka Streams тут аналогичны Orders Service.

Класс EmailService отвечает за создание и обработку `KStream` и `KTable`. Для того,
чтобы после старта приложения создалась и заработала топология стримов Kafka Streams, необходимо
ее настроить с помощью `StreamsBuilder`. Для этого достаточно в класс, помеченный аннотацией `@Component` 
или `@Service` в один из методов заинжектить StreamsBuilder и в этом методе выстроить нужную топологию.

```kotlin
@Service
class EmailService(
    private val emailer: Emailer,
    private val schemas: Schemas
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {
        val orders: KStream<String, Order> = builder.stream(
            schemas.ORDERS.name,
            Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
        )
        //Create the streams/tables for the join
        val payments: KStream<String, Payment> = builder.stream(
            schemas.PAYMENTS.name,
            Consumed.with(schemas.PAYMENTS.keySerde, schemas.PAYMENTS.valueSerde)
        )
            //Rekey payments to be by OrderId for the windowed join
            .selectKey { s, payment -> payment.orderId }

        val customers: GlobalKTable<Long, Customer> = builder.globalTable(
            schemas.CUSTOMERS.name,
            Consumed.with(schemas.CUSTOMERS.keySerde, schemas.CUSTOMERS.valueSerde)
        )

        val serdes: StreamJoined<String, Order, Payment> = StreamJoined
            .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde, schemas.PAYMENTS.valueSerde)
        //Join the two streams and the table then send an email for each
        orders.join(
            payments,
            ::EmailTuple,
            JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)),
            serdes
        )
            //Next join to the GKTable of Customers
            .join(
                customers,
                { _, value -> value.order.customerId },
                // note how, because we use a GKtable, we can join on any attribute of the Customer.
                EmailTuple::setCustomer
            )
            //Now for each tuple send an email.
            .peek { _, emailTuple ->
                emailer.sendEmail(emailTuple)
            }

        //Send the order to a topic whose name is the value of customer level
        orders.join(
            customers,
            { orderId, order -> order.customerId },
            { order, customer ->
                OrderEnriched(order.id, order.customerId, customer.level)
            }
        )
            //TopicNameExtractor to get the topic name (i.e., customerLevel) from the enriched order record being sent
            .to(
                TopicNameExtractor { orderId, orderEnriched, record ->
                    orderEnriched.customerLevel
                },
                Produced.with(schemas.ORDERS_ENRICHED.keySerde, schemas.ORDERS_ENRICHED.valueSerde)
            )
    }

}
```

В данном примере следует обратить внимание на несколько моментов. 
1. `KStream<String, Payment>` создается на основе топика `payments.v1,` в котором ключом является ID платежа,
   при этом в информации о платеже присутствует поле `orderId`, которое фактически ссылается на сущность
   `Order`. Kafka Streams поддерживает объединение `KStream` по ключу топика, из которого вычитываются 
   сообщения. Это называется inner equi join. Но так как в исходных топиках разные ключи, приходится
   провести re-keying - выбор нового ключа в "правом" стриме с помощью метода `KStream.selectKey()`, 
   в нашем случае в `KStream<String, Payment>`. Под капотом Kafka Streams создаст еще один топик с
   таким же количеством партиций, как и в исходном топике и будет отправлять данные из 
   первоначального топика (`payments.v1`) в новый топик. Теперь этот `KStream`, основанный на 
   внутреннем топике можно спокойно объединять со стримом `KStream<String, Order>`, так как у них одинаковые
   ключи записей.

**NB! ВАЖНО** Тут есть один очень важный ньюанс: для того, чтобы Kafka Streams могла провести объединение
данных исходные топики должны быть ко-партиционированы:
- должны быть одинаковые ключи
- должны иметь одинаковое количество партиций
- должны иметь одинаковую [стратегию партиционирования](https://docs.ksqldb.io/en/latest/developer-guide/joins/partition-data/#records-have-same-partitioning-strategy)

Подробнее об этом в статье [Co-Partitioning with Apache Kafka](https://www.confluent.io/blog/co-partitioning-in-kafka-streams/). 

2. Информация о покупателях собирается в `GlobalKTable<Long, Customer>`. Это означает, что каждый
   инстанс приложения Email Service будет иметь в своем распоряжении данные обо всех покупателях, в отличие от
   обычной KTable, которая хранит на инстансе только данные из вычитываемых этим инстансом партиций.
   Таким образом мы можем объединять KStream с GlobalKTable по любому полю, плюс нет необходимости
   соблюдать правило о ко-партиционировании топиков. Однако теперь наш инстанс может быть перегружен 
   как по памяти, так и по сетевому трафику, если топик `customers` будет хранить очень много данных
   и постоянно пополняться. 

3. При объединении стримов важно понимать, что данные в топики, на основе которых создаются стримы
   могут попадать не одновременно. Поэтому при объединении стримов используются "окна" объединения - 
   JoinWindow. В нашем случае при объединении стримов orders и payments мы используем окно в 1 минуту,
   без какого-либо грейс периода.  `JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1))`
   Это означает, что записи, timestamp которых в пределах 1 минуты друг от друга, могут быть объеденины,
   то есть мы подразумеваем, что создание заказа и его оплата по времени не различаются на 1 минуту. 
   Записи, которые не попадают в это окно, не будут учитываться при объединении стримов. 

```java
    /**
     * Specifies that records of the same key are joinable if their timestamps are within {@code timeDifference},
     * i.e., the timestamp of a record from the secondary stream is max {@code timeDifference} before or after
     * the timestamp of the record from the primary stream.
     * <p>
     * CAUTION: Using this method implicitly sets the grace period to zero, which means that any out-of-order
     * records arriving after the window ends are considered late and will be dropped.
     *
     * @param timeDifference join window interval
     * @return a new JoinWindows object with the window definition and no grace period. Note that this means out-of-order records arriving after the window end will be dropped
     * @throws IllegalArgumentException if {@code timeDifference} is negative or can't be represented as {@code long milliseconds}
     */
    public static JoinWindows ofTimeDifferenceWithNoGrace(final Duration timeDifference) {
        return ofTimeDifferenceAndGrace(timeDifference, Duration.ofMillis(NO_GRACE_PERIOD));
    }
```   
Более подробно о том, какие бывают виды объединений можно прочитать в [документации](https://kafka.apache.org/20/documentation/streams/developer-guide/dsl-api.html#joining). 

## Требования для запуска:
1. JDK 17 или новее
2. Docker + docker-compose
3. ОЗУ для Docker не менее 8 Гб
4. ОЗУ для микросервисов не менее 8 Гб. (Точно запускается на Ubuntu 22.04 с 64 Гб ОЗУ и 8 ядрами процессора)

## Как запустить микросервисы
Запустить скрипт из корневой папки:
- Если установлен Docker V2, то: 
```shell
./prepare_script_docker_new.sh
```
- Если более стара версия, то: 
```shell
./prepare_script_docker_old.sh
```
Скрипт осуществляет следующие действия:
1. Поднимает docker контейнеры: 
   - Zookeeper
   - Kafka
   - Schema Registry (Confluent)
   - Connect (Kafka Connect)
   - ksqlDB
   - Elasticsearch
   - SQLite
   - Kibana

2. Регистрирует Avro схему сущностей, которые будут обрабатываться в Kafka. 
   Регистрация осуществляется с помощью Gradle Plugin:  
   `id 'com.github.imflog.kafka-schema-registry-gradle-plugin' version "1.12.0"`

3. Проводит чистую сборку микросервисов (пока без тестов, так как их еще не написал).
4. Создает индекс `orders.v1` в ElasticSearch
5. Конфигурирует Kafka Connect для:
    - получения данных из SQLite (таблица customers) и записи лога изменений в Kafka (паттерн Change Data Capture)
    - записи данных из топика Kafka `orders.v1` в индекс `orders.v1` ElasticSearch
6. Настраивает Kibana для просмотра записей из ElasticSearch.

### Возможные проблемы
У меня на виртуальной машине Ubuntu 22.04 изначально не запустился контейнер elasticsearch,
так как требовалось установить значение vm.max_map_count не ниже 262144. Если возникает 
такая проблема, то можно выполнить следующую команду:
```shell
sudo sysctl -w vm.max_map_count=262144
```

После того, как скрипт отработает, можно запускать микросервисы. 
Самый простой вариант - запустить в IntellijIdea все микросервисы, кроме populator.
Populator следует запустить в последнюю очередь, так как он будет постоянно генерировать данные
и отправлять POST запросы в orders-service на создание заказа (1 заказ в секунду), 
а также посылать сообщения в Kafka топики: warehouse-inventory.v1 и payments.v1.

Можно дать сервису populator поработать несколько секунд (15-30) и после этого остановить.
Чтобы прочитать содержимое топиков Kafka (последние 5 сообщений в топике) необходимо
выполнить следующий скрипт (опять же в зависимости от того, какой docker установлен):
```shell
./read-topics-docker-new.sh
```
или
```shell
./read-dopics-docker-old.sh
```

В конце необходимо остановить docker контейнеры:
```shell
docker compose down
```