#!/bin/bash

printf "\n====== Configuring Elasticsearch mappings\n"
../infra/dashboard/set_elasticsearch_mapping.sh

printf "\n\n====== Configuring Kibana Dashboard\n"
../infra/dashboard/configure_kibana_dashboard.sh

