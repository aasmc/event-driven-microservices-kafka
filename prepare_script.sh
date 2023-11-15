#!/bin/bash

check_connect_up() {
  containerName=$1

  FOUND=$(docker compose logs $containerName | grep "Herder started")
  if [ -z "$FOUND" ]; then
    return 1
  fi
  return 0
}

WARMUP_TIME=180
printf "\n====== Starting infrastructure services in Docker\n"
docker compose up -d --build zookeeper kafka schema-registry ksqldb connect sqlite elasticsearch
printf "\n====== Giving infrastructure services $WARMUP_TIME seconds to startup\n"
sleep $WARMUP_TIME

printf "\n====== Registering Avro schema with Schema Registry\n"
./gradlew registerSchemaTask

printf "\n====== Building Microservices\n"
./gradlew clean build
printf "\n====== Giving microservices $WARMUP_TIME seconds to build\n"
sleep $WARMUP_TIME

printf "\n====== Starting microservices in Docker\n"
docker compose up -d email-service fraud-service order-details-service order-enrichment-service orders-service validation-aggregator-service
printf "\n====== Giving microservices $WARMUP_TIME seconds to startup\n"
sleep $WARMUP_TIME

MAX_WAIT=100
echo "Waiting up to $MAX_WAIT seconds for connect to start"
retry $MAX_WAIT check_connect_up connect || exit 1
printf "\n\n"


printf "\n====== Configuring Elasticsearch mappings\n"
./infra/dashboard/set_elasticsearch_mapping.sh

printf "\n====== Submitting connectors\n\n"
./scripts/create_connectors.sh

printf "\n\n====== Configuring Kibana Dashboard\n"
./infra/dashboard/configure_kibana_dashboard.sh

printf "\n\n====== Reading data from topics and ksqlDB\n"
./read-topics-ccloud.sh