#!/bin/sh
mvn help:evaluate -Dexpression=project.version | grep -e '^[^\[]'
