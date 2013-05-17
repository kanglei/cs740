package net.floodlightcontroller.zkz.learningswitch;

import net.floodlightcontroller.packet.IPv4;

public class ZKZPath {
	private ZKZSwitch	coreSwitch = null;
	private ZKZSwitch	edgeSwitch = null;
	private ZKZSwitch	accessSwitch = null;
	
	private int			outerIP;
	private int			innerIP;
	
	private static String remoteIP = "10.0.10.10";
	
	
	private static ZKZPath calculatePath(int ipA) {
		ZKZPath path = new ZKZPath();
		Long mac = NetworkTopo.getMacAddress(ipA);
		Long upperMac = NetworkTopo.getMacAddress(IPv4.toIPv4Address(remoteIP));
		
		if(null != mac) {
			for(Long id: ZKZSwitch.coreSwitches.keySet()) {
				ZKZSwitch sw = ZKZSwitch.coreSwitches.get(id);
				sw.setUpperPort(upperMac);
		
				if(OVSConfigure.isOpenVSwitch(id) && null!=sw.getPortFromMac(mac)) {
					path.coreSwitch = sw;
					break;
				}
			}
			
			for(Long id: ZKZSwitch.edgeSwitches.keySet()) {
				ZKZSwitch sw = ZKZSwitch.edgeSwitches.get(id);
				sw.setUpperPort(upperMac);
				if(OVSConfigure.isOpenVSwitch(id) && null!=sw.getPortRatherThanUpperPort(mac)) {
					path.edgeSwitch = sw;
					break;
				}
			}
			
			for(Long id: ZKZSwitch.accessSwitches.keySet()) {
				ZKZSwitch sw = ZKZSwitch.accessSwitches.get(id);
				sw.setUpperPort(upperMac);
				
				if(OVSConfigure.isOpenVSwitch(id) && null!=sw.getPortRatherThanUpperPort(mac)) {
					path.accessSwitch = sw;
					break;
				}
			}
		}
		
		return path;
	}
	/**
	 * create ZKZPath based on given IP pair
	 * @param ipA
	 * @param ipB
	 * @return an array of (two) ZKZPath
	 */
	
	public static ZKZPath[] getPath(int ipA, int ipB) {
		//TODO get path between two hosts
		ZKZPath[] paths = new ZKZPath[2];
		paths[0] = calculatePath(ipA);
		paths[0].innerIP = ipA;
		paths[0].outerIP = ipB;
		paths[1] = calculatePath(ipB);
		paths[1].innerIP = ipB;
		paths[1].outerIP = ipA;
		
		return paths;
	}
	
	public ZKZSwitch getCoreSwitch() {
		return coreSwitch;
	}
	
	public ZKZSwitch getEdgeSwitch() {
		return edgeSwitch;
	}
	
	public ZKZSwitch getAccessSwitch() {
		return accessSwitch;
	}
	
	public int getOuterIP() {
		return outerIP;
	}
	
	public int getInnerIP() {
		return innerIP;
	}
}
