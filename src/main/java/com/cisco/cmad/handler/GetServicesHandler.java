package com.cisco.cmad.handler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.cisco.cmad.model.CorporateEntity;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class GetServicesHandler {
	private static MongoClient client;
	static Logger logger = LoggerFactory.getLogger(GetServicesHandler.class); 
	private EventBus eventBus;
	private  MessageConsumer<JsonObject> corporateConsumer;
    public  void handleGetCompanies(RoutingContext rc){
    	client.find("corporateEntity", new JsonObject().put("type","company"), results -> {
    		System.out.println(results.result()+"dasada"+"\n");
    		if (results.succeeded() && results.result() !=null) {
				List<JsonObject> objects = results.result();
				System.out.println("asdaad"+results.result());
				List<CorporateEntity> company = objects.stream().map(CorporateEntity::new).collect(Collectors.toList());;
				int i;
				JsonArray resJson = new JsonArray();
				for (i=0;i<company.size();i++){
					resJson.add(
							new JsonObject()

							.put("id", company.get(i).getId().toString())
							.put("companyName", company.get(i).getName())
							.put("subDomain", "")
						);
			
			}
				rc.response().putHeader("content-type", "application/json").end(resJson.encode());
		}
			else {rc.response().setStatusCode(400).putHeader("content-type", "application/json").end();}
			
	});
    }
    public void setEventBus(EventBus eb){
    	   eventBus =eb;
    	   setMessageConsumers(eb);
    }
    public void setMongoClient(MongoClient mongoClient){
    	client = mongoClient;
    }
    public void handleGetDepartmentsOfSite(RoutingContext rc){
    	System.out.println(rc.request().getParam("siteId"));
    	client.find("corporateEntity", new JsonObject().put("parent",rc.request().getParam("siteId") ).put("type","dept"), results -> {
			System.out.println(results.result()+"dasada"+"\n");
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

private void setMessageConsumers(EventBus eb){
	corporateConsumer = eb.consumer("com.cisco.cmad.register.company");
	corporateConsumer.completionHandler(message -> {
	 	 logger.debug("Get Service registered to BlogServiceBus");
	});
	corporateConsumer.handler(message->{
	   	JsonObject msg = message.body();
	   	logger.error("VAlues"+message.body());
	   	JsonObject returnObj = new JsonObject();
	    CorporateEntity comp = new CorporateEntity(msg.getString("companyName"),"company",Optional.empty(),Optional.empty());
	    CorporateEntity site = new CorporateEntity(msg.getString("siteName"),"site",Optional.empty(),Optional.ofNullable(new JsonObject().put("subDomain",msg.getString("subDomain"))));
	    CorporateEntity dept = new CorporateEntity(msg.getString("deptName"),"dept",Optional.empty(),Optional.empty());
	    logger.debug("message received over com.cisco.cmad.register.company"+message.body());
//		        	JsonObject repl = saveCorporateEntities(msg.getString("companyName"),msg.getString("siteName")
//		        			,msg.getString("deptName"),msg.getString("subDomain"));
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
		                     logger.error("Sent:"+returnObj.encode());
		                     				message.reply(returnObj);
		                  }
			
		                 });
		                 }
		             message.reply(returnObj);
		        });
		  }
		  else {
			  if (logger.isDebugEnabled())
		    	logger.debug("\nFailed to save Company:"+"\n"+saveCompany.result());
		    	client.findOne("corporateEntity", comp.toJson(),null, findCompany->{
		    	if (findCompany.succeeded()){
		    		JsonObject object= findCompany.result();
		    		CorporateEntity ret_company = new CorporateEntity(object);
		    		returnObj.put("companyId",ret_company.getId().toString());
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
		    			    				returnObj.put("siteId",ret_Site.getId().toString());
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
		    			    		       	    				returnObj.put("DeptId",ret_Dept.getId().toString());
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

}
