# Event Driven Microservices With Kafka Streams and ksqlDB
https://docs.confluent.io/platform/current/tutorials/examples/microservices-orders/docs/index.html
https://www.confluent.io/blog/building-a-microservices-ecosystem-with-kafka-streams-and-ksql/

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
   - SqlLite
   - Kibana

2. Регистрирует Avro схему сущностей, которые будут обрабатываться в Kafka. 
   Регистрация осуществляется с помощью Gradle Plugin:  
   `id 'com.github.imflog.kafka-schema-registry-gradle-plugin' version "1.12.0"`

3. Проводит чистую сборку микросервисов (пока без тестов, так как их еще не написал).
4. Создает индекс orders.v1 в ElasticSearch
5. Конфигурирует Kafka Connect для:
    - получения данных из SqlLite (таблица customers) и записи лога изменений в Kafka (паттерн Change Data Capture)
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
Чтобы проситать содержимое топиков Kafka (последние 5 сообщений в топике) необходимо
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