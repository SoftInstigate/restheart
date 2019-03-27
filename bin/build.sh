#!/bin/bash
set -e

mvn clean verify -DskipITs=false -Dkarate.options='$KARATE_OPS'