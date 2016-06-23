package com.cisco.cmad.model;

import java.util.Optional;
import org.bson.types.ObjectId;
import io.vertx.core.json.JsonObject;

public class CorporateEntity {
	private ObjectId id;
	private String Name;
	private ObjectId parent;
	private JsonObject key = new JsonObject();
	private String type;
	public ObjectId getId() {
		return id;
	}
	public void setId(String id) {
		this.id = new ObjectId(id);
	}

	public String getName() {
		return Name;
	}
	public void setName(String Name) {
		this.Name = Name;
	}
	
	public ObjectId getParent() {
		return parent;
	}
	public void setParent(ObjectId parent) {
		this.parent = parent;
	}
	
	public JsonObject getKey() {
		return key;
	}
	public void setKey(JsonObject key) {
		this.key = key;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public JsonObject toJson() {
		    JsonObject json = new JsonObject();
		    if (id != null ) {
					      json.put("_id", id.toString());
					    }
		    	json.put("Name", this.Name)
		        .put("type",this.type);
		        if (!type.equalsIgnoreCase("company"))
		        	if (this.parent !=null )
		        	   json.put("parent",this.parent.toString());
		        if (!key.isEmpty())
		        	json.put("key",key);
		    
		    return json;
	}

	public CorporateEntity(String name, String type,Optional<String> parent,Optional<JsonObject> key) {
		super();
		this.Name = name;
		this.type = type;
		if (parent.isPresent())		this.parent = new ObjectId(parent.get());
		if (key.isPresent()) this.key = key.get();
	}

	public CorporateEntity(JsonObject obj){
		this.id = new ObjectId(obj.getString("_id"));
		this.Name =obj.getString("Name");
		this.type = obj.getString("type");
		if (!type.equalsIgnoreCase("company")) 
			this.parent = new ObjectId(obj.getString("parent"));
		try {
			this.key = obj.getJsonObject("key");
		}
		catch (Exception e){}
		
	}

}
