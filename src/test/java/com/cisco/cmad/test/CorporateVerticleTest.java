package com.cisco.cmad.test;


import org.junit.*;
import org.junit.runner.RunWith;

import com.cisco.cmad.*;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.*;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.ServerSocket;

import io.vertx.core.http.HttpHeaders;
@RunWith(VertxUnitRunner.class)
public class CorporateVerticleTest {

	private int port;
	private Vertx vertx;
	private EventBus eb;
    @Before
	public void before(TestContext context) throws Exception {
    	VertxOptions options = new VertxOptions().setWorkerPoolSize(10); 
//    	options.setMaxEventLoopExecuteTime(Long.MAX_VALUE);
    	vertx = Vertx.vertx(options);
		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();
		 eb = vertx.eventBus();
	DeploymentOptions depoptions = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port).put("port",27017).put("host","localhost").put("db_name","blog_db"));
		vertx.deployVerticle(GetServicesVerticle.class.getName(),depoptions,  context.asyncAssertSuccess());
	}

    @After
    public void after(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }
	@Test
	public void testHomePageFetch(TestContext context) {
		
		final Async async = context.async();

        vertx.createHttpClient().getNow(port, "localhost", "/", resp -> {

            context.assertEquals(200, resp.statusCode(), "Status code should be 200 ");
            resp.bodyHandler(body -> {
                context.assertTrue(body.toString().contains("mysocial"), "Body should contain mysocial tag");
                async.complete();
        });
        });
	}
	
	@Test
	public void setTestCompanies(TestContext context){
		eb.send("com.cisco.cmad.register.company",new JsonObject().put("companyName", "CISCO")
				.put("siteName","SPVSS")
				.put("deptName","Evolution")
				.put("subDomain","com.cisco")				
				,res->{
					if (res.succeeded()){
						JsonObject returnedObj = (JsonObject) res.result();
						context.assertTrue(returnedObj.containsKey("deptId"),"Dept set"+returnedObj.encode());
						
					}

		});
		
		eb.send("com.cisco.cmad.register.company",new JsonObject().put("companyName", "OSN")
				.put("siteName","NEWCO")
				.put("deptName","Evolution")
				.put("subDomain","com.cisco")				
				,res->{
					if (res.succeeded()){
						JsonObject returnedObj = (JsonObject) res.result();
						context.assertTrue(returnedObj.containsKey("companyId"),"Company set"+returnedObj.encode());
						
					}
			
		});
		
	}
	
	@Test
	public void testgetCompanies(TestContext context){

	        Async async = context.async();
	        vertx.createHttpClient().get(port, "localhost", "/Services/rest/company", resp -> {
	        	context.assertEquals(resp.statusCode(), HttpResponseStatus.OK.code());
	            resp.bodyHandler(body -> 
	            {
	      //      	context.assertNotNull(body.toJsonObject());
	            
	                async.complete();
	            }
	            );
	        	
	        }).putHeader(HttpHeaders.CONTENT_LENGTH, "0").end();
	
	}
	@Test
	public void testlistOfSites(TestContext context){

	        Async async = context.async();
	        vertx.createHttpClient().get(port, "localhost", "/Services/rest/company/1/sites", resp -> {
	        	context.assertEquals(resp.statusCode(), HttpResponseStatus.OK.code());
	        	resp.bodyHandler(body -> 
	            {

	                async.complete();
	            }
	            );
	        	
	        }).putHeader(HttpHeaders.CONTENT_LENGTH, "0").end();
	
	}
	@Test
	public void testListOfDept(TestContext context){

	        Async async = context.async();
	        vertx.createHttpClient().get(port, "localhost", "/Services/rest/company/1/sites/2/departments", resp -> {
	        	context.assertEquals(resp.statusCode(), HttpResponseStatus.OK.code());
	            resp.bodyHandler(body -> 
	            {
	            	//context.assertNotNull(body.toJsonObject());	            
	                async.complete();
	            }
	            );
	        	
	        }).putHeader(HttpHeaders.CONTENT_LENGTH, "0").end();
	
	}
	
	}
