package net.floodlightcontroller.zkz.learningswitch;


import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class ZKZWebRoutable implements RestletRoutable {
	
	public ZKZWebRoutable() {
		
	}

	  
	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
	    router.attach("/post/json", ZKZResource.class);
	    return router;
	}

	@Override
	public String basePath() {
		return "/wm/zkz";    
	}
}
