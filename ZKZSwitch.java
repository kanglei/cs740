package net.floodlightcontroller.zkz.learningswitch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.openflow.protocol.OFPhysicalPort;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;

public class ZKZSwitch {
	private static Map<Long, ZKZSwitch> switchSet
			= new HashMap<Long, ZKZSwitch>();
	private static Map<Long, Map<Long, Short>> switchLinks
			= new HashMap<Long, Map<Long, Short>>();

	/*add by Lei*/
	public static Map<Long, ZKZSwitch> coreSwitches
	= new HashMap<Long, ZKZSwitch>();
	public static Map<Long, ZKZSwitch> edgeSwitches
	= new HashMap<Long, ZKZSwitch>();
	public static Map<Long, ZKZSwitch> accessSwitches
	= new HashMap<Long, ZKZSwitch>();
	private Short upperPort;
	public Short getUpperPort() {
		return upperPort;
	}
	public void setUpperPort(Long mac) {
		upperPort = macToPort.get(mac);
	}
	public Short getPortRatherThanUpperPort(Long mac) {
		Short port = macToPort.get(mac);
		if(port!=upperPort)
			return port;
		else
			return null;
	}
	
	synchronized public static ZKZSwitch getSwitch(long id) {
		ZKZSwitch sw = switchSet.get(id);
		if (sw == null) {
			sw = new ZKZSwitch(id);
			switchSet.put(id, sw);
			/*add by Lei*/
			if(sw.isCoreSwitch())
				coreSwitches.put(id,  sw);
			else if(sw.isEdgeSwitch())
				edgeSwitches.put(id,  sw);
			else if(sw.isAccessSwitch())
				accessSwitches.put(id, sw);
			else {}
		}
		return sw;
	}
	
	synchronized public static void setLinkPort(long src, long dst, short port) {
		Map<Long, Short> links = switchLinks.get(src);
		if (links == null) {
			links = new HashMap<Long, Short>();
			switchLinks.put(src, links);
		}
		links.put(dst, port);
	}
	
	synchronized public static Short getLinkPort(long src, long dst) {
		Map<Long, Short> links = switchLinks.get(src);
		if (links == null) {
			links = new HashMap<Long, Short>();
			switchLinks.put(src, links);
		}
		return links.get(dst);
	}

	synchronized public static void configureSwitches(
			IFloodlightProviderService floodlightProvider) {

		Map<Long,IOFSwitch> switches = floodlightProvider.getSwitches();
		Iterator<IOFSwitch> switchIterator = switches.values().iterator();
		while(switchIterator.hasNext())
		{
			IOFSwitch sw = switchIterator.next();
			ZKZSwitch zkzSw = ZKZSwitch.getSwitch(sw.getId());
			for (OFPhysicalPort port : sw.getPorts()) {
				if (port.getPortNumber() > 0)
					zkzSw.addPort(port.getPortNumber());
			}
		}		
	}
	synchronized public static void configureLinks(
						ILinkDiscoveryService linkDiscoveryProvider) {
		
		Map<Link,LinkInfo> links = linkDiscoveryProvider.getLinks();
		Iterator<Link> linkIterator = links.keySet().iterator();
		while(linkIterator.hasNext())
		{
			Link link = linkIterator.next();

			ZKZSwitch srcSwitch = ZKZSwitch.getSwitch(link.getSrc());
			ZKZSwitch dstSwitch = ZKZSwitch.getSwitch(link.getDst());
			setLinkPort(srcSwitch.id, dstSwitch.id, link.getSrcPort());
			setLinkPort(dstSwitch.id, srcSwitch.id, link.getDstPort());
			
			if (srcSwitch.isCoreSwitch()) {
				srcSwitch.mapPortToVLAN(link.getSrcPort(), dstSwitch.outsideVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), dstSwitch.outsideVLAN);

				srcSwitch.mapPortToVLAN(link.getSrcPort(), srcSwitch.bypassVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), dstSwitch.bypassVLAN);

				srcSwitch.setNativeVLAN(link.getSrcPort(), dstSwitch.outsideVLAN);
				dstSwitch.setNativeVLAN(link.getDstPort(), dstSwitch.outsideVLAN);
			}
			else if (dstSwitch.isCoreSwitch()) {
				srcSwitch.mapPortToVLAN(link.getSrcPort(), srcSwitch.outsideVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), srcSwitch.outsideVLAN);
				
				srcSwitch.mapPortToVLAN(link.getSrcPort(), srcSwitch.bypassVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), dstSwitch.bypassVLAN);
				
				srcSwitch.setNativeVLAN(link.getSrcPort(), srcSwitch.outsideVLAN);
				dstSwitch.setNativeVLAN(link.getDstPort(), srcSwitch.outsideVLAN);
			}
			else {
				srcSwitch.mapPortToVLAN(link.getSrcPort(), srcSwitch.defaultVLAN);
				srcSwitch.mapPortToVLAN(link.getSrcPort(), srcSwitch.bypassVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), dstSwitch.defaultVLAN);
				dstSwitch.mapPortToVLAN(link.getDstPort(), dstSwitch.bypassVLAN);
			}
		}
	}
	
	private long				id;
	private Map<Long, Short>	macToPort;
	private Set<Short>			ports;
	
	private Map<Short, Set<Short>>	portToVLAN;
	
	private short outsideVLAN;
	private short bypassVLAN;
	
	private short				defaultVLAN;
	private Map<Short, Short>	nativeVLAN;

	public ZKZSwitch(long id) {
		this.id = id;
		this.macToPort	= new HashMap<Long, Short>();
		this.portToVLAN	= new HashMap<Short, Set<Short>>();
		this.ports		= new HashSet<Short>();
		this.nativeVLAN = new HashMap<Short, Short>();
		
		this.outsideVLAN = (short)(((id & 0xFF00) >> 8) * 10 + 1);
		this.defaultVLAN = (short)(((id & 0xFF00) >> 8) * 10 + 2);
		this.bypassVLAN = (short) 256;
	}

	public long getId() {
		return id;
	}
	
	public void mapMacToPort(long mac, short port) {
       	macToPort.put(mac, port);
	}
	
	public Short getPortFromMac(long mac) {
		return macToPort.get(mac);
	}

	private Set<Short> getVLANTrunk(short port) {
		Set<Short> vlanTrunk = portToVLAN.get(port);
		if (vlanTrunk == null) {
			vlanTrunk = new HashSet<Short>();
			portToVLAN.put(port, vlanTrunk);
		}
		return vlanTrunk;
	}
	
	public void mapPortToVLAN(short port, short vlan) {
		getVLANTrunk(port).add(vlan);
	}
	
	public boolean isVLANInPort(short port, short vlan) {
		return getVLANTrunk(port).contains(vlan);
	}
	
	public void setNativeVLAN(short port, short vlan) {
		this.nativeVLAN.put(port, vlan);
	}
	
	public short getNativeVLAN(short port) {
		Short vlan = nativeVLAN.get(port);
		return (vlan == null ? defaultVLAN : vlan);
	}
	
	public short getBypassVLAN(short port) {
		return bypassVLAN;
	}
	
	public boolean isAccessPort(short port) {
		return (isAccessSwitch() || isCoreSwitch()) &&
			   getVLANTrunk(port).size() == 0;
	}

	public void addPort(short port) {
		ports.add(port);
	}
	
	public Set<Short> getPorts() {
		return ports;
	}
	
	public boolean isCoreSwitch() {
		return (id - (id & 0xFF) == 0);
	}
	
	public boolean isEdgeSwitch() {
		return ((id & 0xFF00) != 0) &&
				(id - (id & 0xFFFF) == 0);
	}

	public boolean isAccessSwitch() {
		return ((id & 0xFF0000) != 0);
	}
	
	public String switchType() {
		if (isCoreSwitch())
			return "core";
		else if (isEdgeSwitch())
			return "edge";
		else if (isAccessSwitch())
			return "access";
		return "unknown";
	}
	
	public static void showInfo() {
		for (ZKZSwitch sw : switchSet.values()) {
			System.err.println("-----------------------------------");
			System.err.printf("%X\n", (int) sw.id);
			for (short port : sw.getPorts()) {
				System.err.print("port: " + port + ", vlan");
				for (short vlan : sw.getVLANTrunk(port)) {
					System.err.print(" " + vlan);
				}
				System.err.println();
			}
		}
	}

	@Override
	public String toString() {
		return "ZKZSwitch " + id;
	}
}
