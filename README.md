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


#### Диаграмма микросервисов и топиков Kafka:
![microservices-diagram.png](art%2Fmicroservices-diagram.png)

Все микросервисы написаны на Kotlin с использованием Kafka Streams и Spring for Apache Kafka. 

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