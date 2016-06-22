#!/bin/sh
#sh scriptname 1
docker build -t godspeed990/get-service .
if [ $? = 0 ]
then
   docker run -i -p 8300:$1 godspeed990/get-service &  
else
   echo "Failed to build and deploy the docker container"
   exit 1
fi