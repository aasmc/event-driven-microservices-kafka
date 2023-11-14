#!/bin/bash

printf "\n====== Configuring Elasticsearch mappings\n"
./infra/dashboard/set_elasticsearch_mapping.sh

printf "\n\n====== Configuring Kibana Dashboard\n"
./infra/dashboard/configure_kibana_dashboard.sh

printf "\n\n====== Reading data from topics and ksqlDB\n"
CONFIG_FILE=/opt/docker/$CONFIG_FILE ./read-topics-ccloud.sh