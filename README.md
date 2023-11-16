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

При создании заказа в Kafka топик orders.v1 посылается соответствующее событие, оно вычитывается
различными сервисами, отвечающими за валидацию заказа: Inventory Service, Fraud Service и Order Details Service.
Эти сервисы параллельно и независимо друг от друга осуществляют валидацию заказа, а результат
PASS или FAIL записывают в топик Kafka order-validations.v1. Validation Aggregator Service 
вычитывает этот топик, аггрегирует результаты по каждому заказу и отправляет конечный результат
в топик orders.v1. 

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

Данные о заказах записываются из топика Kafka orders.v1 в индекс orders.v1 ElasticSearch
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
Для сгенерированных AVRO классов используется io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde,
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
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'com.fasterxml.jackson.module:jackson-module-kotlin'
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
`StreamsConfig.APPLICATION_SERVER_CONFIG` - адрес текущего инстанса в формате host:port. 
Также мы указываем адрес Schema Registry, путь до каталога, где будет хранится state store и т.д.
По умолчанию Kafka Streams использует встроенное key-value хранилище RocksDB в качестве state store.

При старте приложения на основе топика Kafka orders.v1 создается KTable - абстракция Kafka Streams,
которая представляет из себя snapshot состояния (можно провести аналогию с compacted topic) - хранятся
последние записи по ключу в топике. При поступлении новых записей данные в KTable обновляются.
При этом Kafka Streams гарантирует, что в случае падения инстанса, данные не будут потеряны,
так как они помимо локального state store, хранятся в change log топике Kafka.

Для того, чтобы приложение имело возможность получать данные из state store, необходимо
его материализовать.

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
можно указать таймаут, после которого DeferredResult вернет ошибку. При получении заказа
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

При создании заказа, Orders Service отправляет сообщение в топик Kafka orders.v1 с помощью
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
4. Создает индекс orders.v1 в ElasticSearch
5. Конфигурирует Kafka Connect для:
    - получения данных из SQLite (таблица customers) и записи лога изменений в Kafka (паттерн Change Data Capture)
    - записи данных из топика Kafka orders.v1 в индекс orders.v1 ElasticSearch
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