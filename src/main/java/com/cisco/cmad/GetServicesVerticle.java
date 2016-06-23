package com.cisco.cmad;




import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.cisco.cmad.model.CorporateEntity;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class GetServicesVerticle  extends AbstractVerticle {
	static Logger logger = LoggerFactory.getLogger(GetServicesVerticle.class); 
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
                    config().getInteger("https.port", 8300), result -> {
                        if (result.succeeded()) {
                            startFuture.complete();
                        } else {
                            startFuture.fail(result.cause());
                        }
                    }
            );

	} 
	private void configureMongoClient() {
		client = MongoClient.createShared(vertx, config(),"CMAD_Pool");
		JsonObject config = new JsonObject().put("createIndexes","corporateEntity")
				.put("indexes",new JsonArray().add(new JsonObject().put("key",new JsonObject().put("Name",1).put("type",1).put("parent",1)).put("name","CorporateUnique").put("unique",true)));
		client.runCommand("createIndexes",  config, res->{
	            	if (res.succeeded()){
	            		if (logger.isDebugEnabled())
	            			logger.debug("MongoClient configured with"+config.encode());
	            	}
	            	
	            });
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
        httpOpts.setKeyStoreOptions(new JksOptions().setPath("mykeystore.jks").setPassword("cmad.cisco"));
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
   	JsonObject returnObj = new JsonObject();
    CorporateEntity comp = new CorporateEntity(msg.getString("companyName"),"company",Optional.empty(),Optional.empty());
    CorporateEntity site = new CorporateEntity(msg.getString("siteName"),"site",Optional.empty(),Optional.ofNullable(new JsonObject().put("subDomain",msg.getString("subDomain"))));
    CorporateEntity dept = new CorporateEntity(msg.getString("deptName"),"dept",Optional.empty(),Optional.empty());

//        	JsonObject repl = saveCorporateEntities(msg.getString("companyName"),msg.getString("siteName")
//        			,msg.getString("deptName"),msg.getString("subDomain"));
    client.save("corporateEntity", comp.toJson(),saveCompany->{      	
       if (saveCompany.succeeded()){
         		returnObj.put("companyId", saveCompany.result());
                site.setParent(new ObjectId(returnObj.getString("companyId")));
                client.save("corporateEntity", site.toJson(), saveSite->{
                 if (saveSite.succeeded()){
                  	returnObj.put("siteId",saveSite.result());
                  	dept.setParent(new ObjectId(returnObj.getString("siteId")));
                     client.save("corporateEntity",dept.toJson(),saveDept->{
                     			if (saveDept.succeeded()){
                     				returnObj.put("deptId", saveDept.result());
                     				message.reply(returnObj);
                  	    		}
	
                 	       	});
                 }
                 	});
                         }
       else {
    	   if (logger.isDebugEnabled())
    		   logger.debug("\nFailed to save Company:"+"\n"+saveCompany.result());
    	  client.findOne("corporateEntity", comp.toJson(),null, findCompany->{
    			if (findCompany.succeeded()){
    				JsonObject object= findCompany.result();
    				CorporateEntity ret_company = new CorporateEntity(object);
    				returnObj.put("companyId",ret_company.getId().toHexString());
                 	site.setParent(new ObjectId(returnObj.getString("companyId")));
    			       client.save("corporateEntity", site.toJson(), saveSite->{
    			        if (saveSite.succeeded()){
    			         	returnObj.put("siteId",saveSite.result());
    	                 	dept.setParent(new ObjectId(returnObj.getString("siteId")));
    			            client.save("corporateEntity",dept.toJson(),saveDept->{
    			            			if (saveDept.succeeded()){
    			            				returnObj.put("deptId", saveDept.result());
    			            				logger.error(returnObj.encode()+"\n");
    			            				message.reply(returnObj);
    			         	    		}
    			           	    		
    			        	       	});
    			        }
    			        else {
    			        	client.findOne("corporateEntity", site.toJson(),null, findSite->{
    			        		if (findSite.succeeded()){
    			    				JsonObject site_object= findSite.result();
    			    				CorporateEntity ret_Site = new CorporateEntity(site_object);
    			    				returnObj.put("siteId",ret_Site.getId().toHexString());
    			                 	dept.setParent(new ObjectId(returnObj.getString("siteId")));
    			    		        client.save("corporateEntity",dept.toJson(),saveDept->{
    			    		        			if (saveDept.succeeded()){
    			    		        				returnObj.put("deptId", saveDept.result());
    			    		        				logger.error(returnObj.encode()+"\n");
    			    		        				message.reply(returnObj);
    			    		     	    		}
    			    		       	    		else {
    			    		       	         	client.findOne("corporateEntity", dept.toJson(),null, findDept->{
    			    		       	        		if (findDept.succeeded()){
    			    		       	    				JsonObject dept_object= findDept.result();
    			    		       	    				CorporateEntity ret_Dept = new CorporateEntity(dept_object);
    			    		       	    				returnObj.put("DeptId",ret_Dept.getId().toHexString());
    			    		       	    				logger.error(returnObj.encode()+"\n");
    			    		       	    				message.reply(returnObj);
    			    		       	        		}
    			    		       	        	});
    			    		       	    			
    			    		        	    		}
    			    		    	       	});
    			        		}
    			        	});
    			        }

    			        	
    			         	    });
    			}
    		});
       }


        });
  	});
        
        
    }
    public void setRoutes(Router router){
        //GET Operations

        router.get("/Services/rest/company/:companyId/sites").handler(this::handleGetSitesOfCompany);
        router.get("/Services/rest/company/:companyId/sites/:siteId/departments").handler(this::handleGetDepartmentsOfSite);
    	router.get("/Services/rest/company").handler(this::handleGetCompanies);

 //       router.route("/logout").handler(this::handleLogOut);
        //Static handler for resource

        router.route().handler(StaticHandler.create().setCachingEnabled(true).setMaxAgeSeconds(60)::handle);

        //For any exceptions that are not taken care of in code
//        router.route().failureHandler(rc->{
//	        int failCode = rc.statusCode();
//	        logger.error("In FailureHandler, Status code :" + failCode);	      
//	        rc.response().setStatusCode(failCode).end();
//        });
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


    
public JsonObject saveCorporateEntities(String companyName,String siteName,String deptName,String subdomain){
	JsonObject returnObj = new JsonObject();
	CountDownLatch latch = new CountDownLatch(1);
    CorporateEntity comp = new CorporateEntity(companyName,"company",Optional.empty(),Optional.empty());
    CorporateEntity site = new CorporateEntity(siteName,"site",Optional.empty(),Optional.ofNullable(new JsonObject().put("subDomain",subdomain)));
    CorporateEntity dept = new CorporateEntity(deptName,"dept",Optional.empty(),Optional.empty());			
  
    	try {latch.await();
    		logger.error("\n"+returnObj);
	    	return returnObj;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error("Exception in latching :"+e.getStackTrace());
		}
    	return returnObj;
    }

public static void main(String args[]){
	
	ClusterManager mgr = new HazelcastClusterManager();
	VertxOptions options = new VertxOptions().setWorkerPoolSize(Integer.parseInt(args[1])).setClusterManager(mgr);
	Vertx.clusteredVertx(options, res -> {
	  if (res.succeeded()) {
	    Vertx vertx = res.result();
	    DeploymentOptions depOptions = new DeploymentOptions().setConfig(new JsonObject().put("https.port", Integer.parseInt(args[0])));
	    vertx.deployVerticle(GetServicesVerticle.class.getName(),depOptions);
	    eventBus = vertx.eventBus();
	  } else {
	    logger.error("Verticle GetServices Deployment on port"+args[0]+"failed");
	  }
	});
}


}



