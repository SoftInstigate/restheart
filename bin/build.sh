#!/bin/bash
mvn clean verify -DskipITs=false -Dkarate.options="$KARATE_OPS"
