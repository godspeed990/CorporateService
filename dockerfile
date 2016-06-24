# Extend vert.x image                       (1)
FROM vertx/vertx3

# Set the name of the verticle to deploy    (2)
ENV VERTICLE_NAME com.cisco.cmad.GetServicesVerticle
ENV VERTICLE_FOL target
ENV VERTICLE_FILE GetService-0.0.1-SNAPSHOT-fat.jar
ENV VERTICLE_OL GetServices.conf
# Set the location of the verticles         (3)
ENV VERTICLE_HOME /opt/verticles

#EXPOSE 8300

# Copy your verticle to the container       (4)
COPY $VERTICLE_FOL $VERTICLE_HOME/
COPY $VERTICLE_OL $VERTICLE_HOME/
# Launch the verticle                       (5)
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["vertx run $VERTICLE_NAME -cp $VERTICLE_FILE  -cluster -conf GetServices.conf DLISTEN_PORT=$LISTEN_PORT"]
