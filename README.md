# ZkCopy

A Tool for copying ZooKeeper data from one cluster to another.

Forked from https://code.google.com/p/zkcopy/

## Building

mvn package

## Running

java 
    -Dlogger.config="log4j.properties" 
    -Dsource="server:port/path" 
    -Ddestination="server:port/path" 
    -Dthreads=10 
-jar target/zkcopy-1.0-jar-with-dependencies.jar
