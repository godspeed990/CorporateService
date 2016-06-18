package com.cisco.cmad;




import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.cisco.cmad.model.CorporateEntity;
import java.util.Optional;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

public class GetServicesVerticle  extends AbstractVerticle {
	Logger logger = LoggerFactory.getLogger(GetServicesVerticle.class); 
	static EventBus eventBus;
	private HttpServer server;
	private static MongoClient client;
	private  MessageConsumer<JsonObject> corporateConsumer;
    public void start(Future<Void> startFuture) throws Exception {
		   logger.info("GetServicesVerticle started " + Thread.currentThread().getId());
	        //Router object is responsible for dispatching the HTTP requests to the right handler
	        Router router = Router.router(vertx);
	        eventBus = getVertx().eventBus();
	        setUpHttpServer(router);
	        setMessageConsumers(eventBus);
	        configureMongoClient();
	        server.requestHandler(router::accept)
            .listen(
                    config().getInteger("https.port", 8443), result -> {
                        if (result.succeeded()) {
                            startFuture.complete();
                        } else {
                            startFuture.fail(result.cause());
                        }
                    }
            );

	} 
	private void configureMongoClient() {
		client = MongoClient.createShared(vertx, new JsonObject().put("db_name", "blog_db"),"CMAD_Pool");
	}
    private void setUpHttpServer(Router router){
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
        httpOpts.setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("cmad@cisco"));
        httpOpts.setSsl(true);
        server = vertx.createHttpServer(httpOpts);



        
        try {
			//setUp("blog_db");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private void setMessageConsumers(EventBus eb){
        corporateConsumer = eb.consumer("com.cisco.cmad.register.company");
        corporateConsumer.completionHandler(message -> {
        	 logger.debug("Get Service registered to BlogServiceBus");
        });
        corporateConsumer.handler(message->{
        	JsonObject msg = message.body();
        	CountDownLatch latch = new CountDownLatch(1);
        	JsonObject repl = saveCorporateEntities(msg.getString("companyName"),msg.getString("siteName")
        			,msg.getString("deptName"),msg.getString("subDomain"),latch);
        	try {
				latch.await();
	        	message.reply(repl);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        });
 
        
        
    }
    public void setRoutes(Router router){
        //GET Operations

        router.get("/Services/rest/company/:companyId/sites").handler(this::handleGetSitesOfCompany);
        router.get("/Services/rest/company/:companyId/sites/:siteId/departments").handler(this::handleGetDepartmentsOfSite);
    	router.get("/Services/rest/user").handler(this::handleLoadSignedInUser);
    	router.get("/Services/rest/company").handler(this::handleGetCompanies);

 //       router.route("/logout").handler(this::handleLogOut);
        //Static handler for resource

        router.route().handler(StaticHandler.create().setCachingEnabled(true).setMaxAgeSeconds(60)::handle);

        //For any exceptions that are not taken care of in code
        router.route().failureHandler(rc->{
	        int failCode = rc.statusCode();
	        logger.error("In FailureHandler, Status code :" + failCode);
	        HttpServerResponse response = rc.response();
	        response.setStatusCode(failCode).end();
        });
    }
    public  void handleGetCompanies(RoutingContext rc){
    	client.find("corporateEntity", new JsonObject().put("type","company"), results -> {
			if (results.succeeded() && results.result() !=null) {
				List<JsonObject> objects = results.result();
				List<CorporateEntity> sites = objects.stream().map(CorporateEntity::new).collect(Collectors.toList());;
				int i;
				JsonArray resJson = new JsonArray();
				for (i=0;i<sites.size();i++){
					resJson.add(
							new JsonObject()

							.put("id", sites.get(i).getId().toString())
							.put("companyName", sites.get(i).getName())
							.put("subDomain", "")
						);
			
			}
				rc.response().putHeader("content-type", "application/json").end(resJson.encode());
		}
			else {rc.response().setStatusCode(400).putHeader("content-type", "application/json").end();}
			
	});
    }
    public void handleLoadSignedInUser(RoutingContext rc){
    	 eventBus.send("com.cisco.cmad.users.signed", new JsonObject(), reply -> {
    		 if (reply.succeeded()) {
    			 Object respObj = reply.result();
    			 JsonArray users_l = (JsonArray) respObj;
    			 rc.response().setStatusCode(200);
    			 rc.response().end(Json.encode(users_l));
    			 
    		 }
    		 
    	 });
    }
    public void handleGetDepartmentsOfSite(RoutingContext rc){
    	client.find("corporateEntity", new JsonObject().put("parent",rc.request().getParam("siteId") ).put("type","dept"), results -> {
			if (results.succeeded() && results.result() !=null) {
				List<JsonObject> objects = results.result();
				List<CorporateEntity> dept = objects.stream().map(CorporateEntity::new).collect(Collectors.toList());;
				int i;
				JsonArray resJson = new JsonArray();
				for (i=0;i<dept.size();i++){
					resJson.add(
							new JsonObject()

							.put("id", dept.get(i).getId().toString())
							.put("deptName", dept.get(i).getName())
							.put("siteId",rc.request().getParam("siteId"))
						);
			
			}
				rc.response().putHeader("content-type", "application/json").end(resJson.encode());
		}
			else {rc.response().setStatusCode(400).putHeader("content-type", "application/json").end();}
			
	});
    }
    
    public void handleGetSitesOfCompany(RoutingContext rc){
    	client.find("corporateEntity", new JsonObject().put("parent",rc.request().getParam("companyId") ).put("type","site"), results -> {
			if (results.succeeded() && results.result() !=null) {
				List<JsonObject> objects = results.result();
				List<CorporateEntity> sites = objects.stream().map(CorporateEntity::new).collect(Collectors.toList());;
				int i;
				JsonArray resJson = new JsonArray();
				for (i=0;i<sites.size();i++){
					resJson.add(
							new JsonObject()

							.put("id", sites.get(i).getId().toString())
							.put("siteName", sites.get(i).getName())

							.put("companyId",rc.request().getParam("companyId"))
							.put("subdomain", sites.get(i).getKey().getString("subDomain"))
						);
			
			}
				rc.response().putHeader("content-type", "application/json").end(resJson.encode());
		}
			else {rc.response().setStatusCode(400).putHeader("content-type", "application/json").end();}
			
	});
    }

    public void handleLogOut(RoutingContext rc){
        logger.info("Logout called");
        // Redirect back to the index page
        rc.response()
//                .putHeader("location", "/#/login")
                .setStatusCode(302)
                .end();
    }
    
    public JsonObject saveCorporateEntities(String companyName,String siteName,String deptName,String subdomain,CountDownLatch latch){
    	JsonObject returnObj = new JsonObject();
    	CorporateEntity comp = new CorporateEntity(companyName,"company",Optional.empty(),Optional.empty());
    	client.save("corporateEntity", comp.toJson(),saveCompany->{
    		if (saveCompany.succeeded()){
    			returnObj.put("companyId", saveCompany.result());
    	    	CorporateEntity site = new CorporateEntity(siteName,"site",Optional.of(saveCompany.result()),Optional.ofNullable(new JsonObject().put("subDomain",subdomain)));
    	    	client.save("corporateEntity", site.toJson(), saveSite->{
    	    		if (saveSite.succeeded()){
    	    			returnObj.put("siteId",saveSite.result());
    	    	    	CorporateEntity dept = new CorporateEntity(deptName,"dept",Optional.of(saveSite.result()),Optional.empty());
    	    	    	client.save("corporateEntity",dept.toJson(),saveDept->{
    	    	    		if (saveDept.succeeded()){
    	    	    			returnObj.put("deptId", saveDept.result());
    	    	    			latch.countDown();
    	    	    		}
    	    	    	});
    	    		}
    	    	});
    		}
    		
    	});
    	try {
			latch.await();
	    	return returnObj;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return returnObj;
    }

}



