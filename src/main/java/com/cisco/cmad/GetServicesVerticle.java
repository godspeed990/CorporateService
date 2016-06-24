package com.cisco.cmad;


import com.cisco.cmad.handler.GetServicesHandler;
import com.google.inject.Guice;
import com.google.inject.Inject;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.BodyHandler;


public class GetServicesVerticle  extends AbstractVerticle {
	static Logger logger = LoggerFactory.getLogger(GetServicesVerticle.class); 
	static EventBus eventBus;
	private HttpServer server;
	private MongoClient client;
	@Inject GetServicesHandler getHandler;

    public void start(Future<Void> startFuture) throws Exception {
		   logger.info("GetServicesVerticle started " + Thread.currentThread().getId());
	        //Router object is responsible for dispatching the HTTP requests to the right handler
	        Router router = Router.router(vertx);
	        Guice.createInjector().injectMembers(this);
	        eventBus = getVertx().eventBus();
	        setUpHttpServer(router);
	        configureMongoClient();
	        getHandler.setEventBus(eventBus);
	        getHandler.setMongoClient(client);
	        int port = 8300;
	        try{
	         port = Integer.parseInt(System.getenv("LISTEN_PORT"));
	        }
	        catch (Exception e){
	        	logger.error("Failed to get ENV PORT");
	        }
	        server.requestHandler(router::accept)
            .listen(config().getInteger("http.port",port), result -> {
                        if (result.succeeded()) {
                        	logger.error("Get Services Verticles running over");
                            startFuture.complete();
                        } else {
                        	logger.error("Get Services Verticles failed to startover");
                            startFuture.fail(result.cause());
                        }
                    }
            );

	} 

    private void setUpHttpServer(Router router){
    	router.route().handler(BodyHandler.create());
    	router.route().handler(ctx -> {
            ctx.response()
                    .putHeader("Cache-Control", "no-store, no-cache")
                    .putHeader("X-Content-Type-Options", "nosniff")
                    .putHeader("X-Download-Options", "noopen")
                    .putHeader("X-XSS-Protection", "1; mode=block")
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, POST")
                    .putHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type, Authorization")
                    .putHeader("X-FRAME-OPTIONS", "DENY");
        	logger.debug("Headers set",ctx.response().toString());
            ctx.next();
        });

        setRoutes(router);
    	HttpServerOptions httpOpts = new HttpServerOptions();
  //      httpOpts.setKeyStoreOptions(new JksOptions().setPath("mykeystore.jks").setPassword("cmad.cisco"));
//        httpOpts.setSsl(true);
        server = vertx.createHttpServer(httpOpts);
    }
    
	private void configureMongoClient() {
		client = MongoClient.createShared(vertx, config());
		JsonObject config = new JsonObject().put("createIndexes","corporateEntity")
				.put("indexes",new JsonArray().add(new JsonObject().put("key",new JsonObject().put("Name",1).put("type",1).put("parent",1)).put("name","CorporateUnique").put("unique",true)));
		client.runCommand("createIndexes",  config, res->{
	            	if (res.succeeded()){
	            		if (logger.isDebugEnabled())
	            			logger.error("MongoClient configured with"+config.encode());
	            	}
	            	
	            });
		}
    

    public void setRoutes(Router router){
        //GET Operations

        router.get("/Services/rest/company/:companyId/sites").handler(getHandler::handleGetSitesOfCompany);
        router.get("/Services/rest/company/:companyId/sites/:siteId/departments").handler(getHandler::handleGetDepartmentsOfSite);
    	router.get("/Services/rest/company").handler(getHandler::handleGetCompanies);

        router.route().handler(StaticHandler.create().setCachingEnabled(true).setMaxAgeSeconds(60)::handle);

    }
	

}



