#!/bin/sh

mvn clean package;
sudo mkdir -p build
sudo cp core/target/restheart.jar build;
sudo cp -r core/etc build;
sudo cp -r core/target/plugins build;
cd build;
java -Xdebug -Xrunjdwp:transport=dt_socket,address=4000,server=y,suspend=n -jar restheart.jar etc/restheart.yml -e etc/default.properties;
