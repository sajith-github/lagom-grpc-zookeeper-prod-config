#!/bin/bash
docker system prune -f
sbt clean docker:publishLocal
#docker-compse -f 
docker-compose -f docker-compose-all-run.yml up
