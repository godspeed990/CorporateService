# Extend vert.x image                       (1)
FROM vertx/vertx3

# Set the name of the verticle to deploy    (2)
ENV VERTICLE_NAME com.cisco.cmad.verticle.GetServicesVerticle
ENV VERTICLE_FILE target/GetService-0.0.1-SNAPSHOT.jar

# Set the location of the verticles         (3)
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8300
EXPOSE 54327

# Copy your verticle to the container       (4)
COPY $VERTICLE_NAME $VERTICLE_HOME/

# Launch the verticle                       (5)
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["vertx run $VERTICLE_NAME -cp $VERTICLE_HOME -instances 20 -worker -cluster -conf conf/GetServices.conf/*"]