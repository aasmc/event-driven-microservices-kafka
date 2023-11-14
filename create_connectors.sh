#!/bin/bash

printf "\n====== Submitting connectors\n\n"
printf "====== Submitting Kafka Connector to source customers from sqlite3 database and produce to topic 'customers'\n"
INPUT_FILE=./infra/connectors/connector_jdbc_customers_template.config
OUTPUT_FILE=./infra/connectors/rendered-connectors/connector_jdbc_customers.config
SQLITE_DB_PATH=/opt/docker/db/data/microservices.db
source ./scripts/render-connector-config.sh
curl -s -S -XPOST -H Accept:application/json -H Content-Type:application/json http://localhost:8083/connectors/ -d @$OUTPUT_FILE

printf "\n\n====== Submitting Kafka Connector to sink records from 'orders' topic to Elasticsearch\n"
INPUT_FILE=./infra/connectors/connector_elasticsearch_template.config
OUTPUT_FILE=./infra/connectors/rendered-connectors/connector_elasticsearch.config
ELASTICSEARCH_URL=http://elasticsearch:9200
source ./scripts/render-connector-config.sh
curl -s -S -XPOST -H Accept:application/json -H Content-Type:application/json http://localhost:8083/connectors/ -d @$OUTPUT_FILE