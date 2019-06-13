#!/bin/bash
# Initialize a MongoDB Replica Set with rs.initiate()
mongo --host mongodb -u restheart --authenticationDatabase=admin -p R3ste4rt! --eval "let res = rs.status(); if (res.codeName !== 'NoReplicationEnabled') { rs.initiate(); };"
