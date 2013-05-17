package net.floodlightcontroller.zkz.learningswitch;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class ZKZResource extends ServerResource {
	 
	@Get("json")    
	 public String retrieve() {    
		return "zkz";
	}
	
    @Post
    public String handlePost(String input) {
        IZKZService zkzService = 
                (IZKZService)getContext().getAttributes().
                get(IZKZService.class.getCanonicalName());
        String[] str = input.split(",");
        zkzService.addIP(str[0], str[1]);
        //return ("{\"status\" : \"subnet mask set\"}");
        return "ip pair:{" + input + "} added\n";
    }
    
   
}
