package net.floodlightcontroller.zkz.learningswitch;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IZKZService extends IFloodlightService {
	
	public String test();
	
	public void addIP(String ip, String dst);

}
