#!/bin/bash

TOPICS_FILE="$@"
BOOTSTRAP_SERVERS=${BOOTSTRAP_SERVERS:-"localhost:9092"}
PARTITIONS=${PARTITIONS:-3}
REPLICATION_FACTOR=${REPLICATION_FACTOR:-3}

while IFS= read -r TOPIC; 
  do
    [[ -z "$TOPIC" ]] || {
      kafka-topics --create --bootstrap-server $BOOTSTRAP_SERVERS --partitions $PARTITIONS --replication-factor $REPLICATION_FACTOR --topic $TOPIC $ADDITIONAL_ARGS 2>/dev/null
    }
  done <$TOPICS_FILE

