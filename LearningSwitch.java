package net.floodlightcontroller.zkz.learningswitch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*	
 * since we are listening to OpenFlow messages we need to 
 * register with the FloodlightProvider (IFloodlightProviderService class
 */
public class LearningSwitch implements 	IOFMessageListener, IFloodlightModule, IZKZService {

	class IPPair {
		String ipA;
		String ipB;
	}
	//	private ArrayList<IPPair> ips = new ArrayList<IPPair>();

	protected IRestApiService restApi;
	public String test() {
		return "test";
	}
	public void addIP(String src, String dst) {
		//		IPPair pair = new IPPair();
		//		pair.ipA = src;
		//		pair.ipB = dst;
		//		ips.add(pair);

		activateBypassRule(src, dst);
	}

	class NetChangeListener implements IOFSwitchListener, ILinkDiscoveryListener, IDeviceListener {
		@Override
		public void linkDiscoveryUpdate(LDUpdate update) {
			configureLinks();
		}

		@Override
		public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
			configureLinks();
		}

		@Override
		public void addedSwitch(IOFSwitch sw) {
			configureSwitches();
		}

		@Override
		public void removedSwitch(IOFSwitch sw) {
			configureSwitches();
		}

		@Override
		public void switchPortChanged(Long switchId) {
			configureSwitches();
		}

		@Override
		public boolean isCallbackOrderingPrereq(String type, String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(String type, String name) {
			return false;
		}

		@Override
		public void deviceAdded(IDevice device) {
		}

		@Override
		public void deviceRemoved(IDevice device) {
		}

		@Override
		public void deviceMoved(IDevice device) {
		}

		@Override
		public void deviceIPV4AddrChanged(IDevice device) {
			configureNetworkTopo();
		}

		@Override
		public void deviceVlanChanged(IDevice device) {
		}

		@Override
		public String getName() {
			return null;
		}
	}

	// Interface to Floodlight core for interacting with connected switches
	protected IFloodlightProviderService floodlightProvider;

	// Interface to link discovery service
	protected ILinkDiscoveryService linkDiscoveryProvider;

	// Interface to device manager service
	protected IDeviceService deviceProvider;

	// Interface to the logging system
	protected static Logger logger;

	// 0 - NOTHING, 1 - HUB, 2 - LEARNING_SWITCH_WO_RULES, 3 - LEARNING_SWITCH_WITH_RULES
	protected static int CTRL_LEVEL = 3;
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 20; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite

	/*
	 * important to override 
	 * put an ID for our OFMessage listener
	 * */
	@Override
	public String getName() {
		return LearningSwitch.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();   
		l.add(IZKZService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IZKZService.class, this);
		return m;
	}

	/*
	 * important to override 
	 * need to wire up to the module loading system by telling the 
	 * module loader we depend on it 
	 * */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService >> floodlightService = 
				new ArrayList<Class<? extends IFloodlightService>>();
		floodlightService.add(IFloodlightProviderService.class);

		floodlightService.add(ILinkDiscoveryService.class);
		floodlightService.add(IDeviceService.class);

		floodlightService.add(IRestApiService.class);
		return floodlightService;
	}

	/*
	 * important to override 
	 * load dependencies and initialize datastructures
	 * */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider		= context.getServiceImpl(IFloodlightProviderService.class);
		linkDiscoveryProvider	= context.getServiceImpl(ILinkDiscoveryService.class);
		deviceProvider			= context.getServiceImpl(IDeviceService.class);
		restApi					= context.getServiceImpl(IRestApiService.class);
		logger					= LoggerFactory.getLogger(LearningSwitch.class);
	}

	/*
	 * important to override 
	 * implement the basic listener - listen for PACKET_IN messages
	 * */
	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		restApi.addRestletRoutable(new ZKZWebRoutable());

		NetChangeListener ncListener = new NetChangeListener();
		floodlightProvider.addOFSwitchListener(ncListener);
		linkDiscoveryProvider.addListener(ncListener);
		deviceProvider.addListener(ncListener);
	}

	private void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi, short outport) {
		pushPacket(sw, match, pi, outport, (short) -1);
	}

	/*
	 * push a packet-out to the switch
	 * */
	private void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi, short outport, 
			short vlan) {

		// create an OFPacketOut for the pushed packet
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);        

		// update the inputPort and bufferID
		po.setInPort(pi.getInPort());
		po.setBufferId(pi.getBufferId());

		// define the actions to apply for this packet
		ArrayList<OFAction> actions = new ArrayList<OFAction>(); 
		int vlanActionLen = 0;
		if (vlan >= 0) {
			OFActionVirtualLanIdentifier vlanTag = 
					new OFActionVirtualLanIdentifier();
			vlanTag.setVirtualLanIdentifier(vlan);
			vlanActionLen = OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
			actions.add(vlanTag);
		}
		OFActionOutput actionOut = new OFActionOutput();
		actionOut.setPort(outport);
		actions.add(actionOut);

		po.setActions(actions);
		po.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH + vlanActionLen));

		// set data if it is included in the packet in but buffer id is NONE
		if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
			byte[] packetData = pi.getPacketData();
			po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
					+ po.getActionsLength() + packetData.length));
			po.setPacketData(packetData);
		} else {
			po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
					+ po.getActionsLength()));
		}        

		// push the packet to the switch
		try {
			sw.write(po, null);
		} catch (IOException e) {
			logger.error("failed to write packetOut: ", e);
		}
	}

	/*
	 * control logic which install static rules 
	 * */
	private Command ctrlLogicWithRules(IOFSwitch sw, OFPacketIn pi) {
		ZKZSwitch zkzSw = ZKZSwitch.getSwitch(sw.getId());

		// Read in packet data headers by using an OFMatch structure
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		// take the source and destination mac from the packet
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac   = Ethernet.toLong(match.getDataLayerDestination());

		Short inputPort	= pi.getInPort();
		zkzSw.mapMacToPort(sourceMac, inputPort);
		Short outPort	= zkzSw.getPortFromMac(destMac);
		short pktVLAN	= match.getDataLayerVirtualLan();

		//    	if (zkzSw.isCoreSwitch()) {
		//    		logger.info("core switch: in port = " + inputPort + 
		//    				", packet vlan = " + match.getDataLayerVirtualLan());
		//    	}

		if (pktVLAN >=0 && !zkzSw.isVLANInPort(inputPort, pktVLAN)) {
			return Command.STOP;
		}

		// if an entry does exist for destMac, flood the packet
		if (outPort == null) {
			this.pushPacket(sw, match, pi, (short)OFPort.OFPP_FLOOD.getValue());
		}
		else {
			short outVLAN = zkzSw.isAccessPort(outPort) ?
								-1 : zkzSw.getNativeVLAN(outPort);
			if (pktVLAN >= 0 && outVLAN >= 0 &&
					!zkzSw.isVLANInPort(outPort, pktVLAN) &&
					!zkzSw.isCoreSwitch()) {
				return Command.CONTINUE;
			}

			// otherwise install a rule s.t. all the traffic with the destination
			// destMac should be forwarded on outPort

			// create the rule and specify it's an ADD rule
			OFFlowMod rule = new OFFlowMod();
			rule.setType(OFType.FLOW_MOD);
			rule.setCommand(OFFlowMod.OFPFC_ADD);

			if (pktVLAN < 0)
				match.setWildcards(~OFMatch.OFPFW_DL_DST);
			else
				match.setWildcards(~(OFMatch.OFPFW_DL_DST | OFMatch.OFPFW_DL_VLAN));

			rule.setMatch(match);

			// specify timers for the life of the rule
			rule.setIdleTimeout(LearningSwitch.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
			rule.setHardTimeout(LearningSwitch.FLOWMOD_DEFAULT_HARD_TIMEOUT);

			// set the buffer id to NONE - implementation artifact
			rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);

			// set of actions to apply to this rule
			ArrayList<OFAction> actions = new ArrayList<OFAction>();

			int spLen = 0;
			if (!(zkzSw.isEdgeSwitch() && pktVLAN >= 0) &&
				zkzSw.isVLANInPort(outPort, outVLAN)) {
				OFActionVirtualLanIdentifier vlanTag =
						new OFActionVirtualLanIdentifier();
				vlanTag.setVirtualLanIdentifier(outVLAN);
				actions.add(vlanTag);
				spLen += OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
			}
			else if (pktVLAN >= 0 && outVLAN < 0) {
				OFActionStripVirtualLan vlanStrip =
						new OFActionStripVirtualLan();
				actions.add(vlanStrip);
				spLen += OFActionStripVirtualLan.MINIMUM_LENGTH;
			}

			OFAction outputTo = new OFActionOutput(outPort);
			actions.add(outputTo);
			rule.setActions(actions);

			// specify the length of the flow structure created
			rule.setLength((short) (OFFlowMod.MINIMUM_LENGTH + 
					spLen +
					OFActionOutput.MINIMUM_LENGTH)); 			

			logger.debug("install rule for destination {}", destMac);

			try {
				sw.write(rule, null);
				sw.flush();
			} catch (Exception e) {
				e.printStackTrace();
			}	

			// push the packet to the switch
			this.pushPacket(sw, match, pi, outPort, (short)outVLAN);
		}       

		return Command.STOP;
	}


	/*
	 * control logic which handles each packet in
	 */
	private Command ctrlLogicWithoutRules(IOFSwitch sw, OFPacketIn pi) {

		// Read in packet data headers by using OFMatch
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		// take the source and destination mac from the packet
		Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
		Long destMac   = Ethernet.toLong(match.getDataLayerDestination());

		Short inputPort = pi.getInPort();

		// if the (sourceMac, port) does not exist in MAC table
		//		add a new entry
		ZKZSwitch zkzSw = ZKZSwitch.getSwitch(sw.getId());
		zkzSw.mapMacToPort(sourceMac, inputPort);

		// if the destMac is in the MAC table take the outPort and send it there
		Short outPort = zkzSw.getPortFromMac(destMac);

		this.pushPacket(sw, match, pi, 
				(outPort == null) ? (short)OFPort.OFPP_FLOOD.getValue() : outPort);

		return Command.CONTINUE;
	}

	/*
	 * hub implementation
	 * */
	private Command ctrlLogicHub(IOFSwitch sw, OFPacketIn pi) {

		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);
		po.setBufferId(pi.getBufferId())
		.setInPort(pi.getInPort());

		// set actions
		OFActionOutput action = new OFActionOutput()
		.setPort((short) OFPort.OFPP_FLOOD.getValue());
		po.setActions(Collections.singletonList((OFAction)action));
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

		// set data if is is included in the packetin
		if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
			byte[] packetData = pi.getPacketData();
			po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
					+ po.getActionsLength() + packetData.length));
			po.setPacketData(packetData);
		} else {
			po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
					+ po.getActionsLength()));
		}
		try {
			sw.write(po, null);
		} catch (IOException e) {
			logger.error("Failure writing PacketOut", e);
		}

		return Command.CONTINUE;
	}


	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		OFMatch match = new OFMatch();
		match.loadFromPacket(((OFPacketIn)msg).getPacketData(), 
				((OFPacketIn)msg).getInPort());

		//    	logger.info("-------------------------------------------");
		//        logger.info("switch: " + sw.getStringId() + ", VLAN: " + match.getDataLayerVirtualLan());

		switch (msg.getType()) {

		case PACKET_IN:
			if (LearningSwitch.CTRL_LEVEL == 1)
				return this.ctrlLogicHub(sw, (OFPacketIn) msg);
			else if (LearningSwitch.CTRL_LEVEL == 2)
				return this.ctrlLogicWithoutRules(sw, (OFPacketIn) msg);					
			else if (LearningSwitch.CTRL_LEVEL == 3)
				return this.ctrlLogicWithRules(sw, (OFPacketIn) msg);

		default:
			break;
		}

		logger.error("received an unexpected message {} from switch {}", msg, sw);
		return Command.STOP;
	}


	public void configureLinks() {
		ZKZSwitch.configureLinks(linkDiscoveryProvider);
	}

	public void configureSwitches() {
		ZKZSwitch.configureSwitches(floodlightProvider);
	}
	public void configureNetworkTopo() {
		NetworkTopo.configureTopo(floodlightProvider,
				linkDiscoveryProvider,
				deviceProvider);
	}


	public void activateBypassRule(String ipA, String ipB) {
		System.err.println(ipA + " -- " + ipB);
		ZKZPath[] zkzPaths = ZKZPath.getPath(
				IPv4.toIPv4Address(ipA),
				IPv4.toIPv4Address(ipB)
				);

		for (ZKZPath path : zkzPaths) {
			System.err.println(path.getCoreSwitch() + " -> " +
					path.getEdgeSwitch() + " -> " +
					path.getAccessSwitch());
			
			int srcIP = path.getOuterIP();
			int dstIP = path.getInnerIP();
			
			if (path.getEdgeSwitch() != null) {
				ZKZSwitch edgeSwitch = path.getEdgeSwitch();
				short innerPort = edgeSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(dstIP));
				short outerPort = edgeSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(srcIP));

				short innerVLAN = edgeSwitch.getNativeVLAN(innerPort);
				short outerVLAN = edgeSwitch.getNativeVLAN(outerPort);
				//System.err.println(edgeSwitch + ", inner " + innerPort + " -- " + innerVLAN +  
				//", outer " + outerPort + " -- " + outerVLAN);

				IOFSwitch sw = floodlightProvider.getSwitches().get(edgeSwitch.getId());
				pushIPBypass(sw, srcIP, outerPort, dstIP, innerPort, innerVLAN);
				pushIPBypass(sw, dstIP, innerPort, srcIP, outerPort, outerVLAN);
			}
			else if (path.getCoreSwitch() != null && path.getAccessSwitch() != null) {
				ZKZSwitch coreSwitch = path.getCoreSwitch();
				ZKZSwitch accessSwitch = path.getAccessSwitch();

				short coreSrcPort = coreSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(srcIP));
				short coreDstPort = coreSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(dstIP));
				IOFSwitch coreSw = floodlightProvider.getSwitches().get(coreSwitch.getId());
				short bypassVLAN = coreSwitch.getBypassVLAN(coreDstPort);
				
				pushIPBypass(coreSw, srcIP, coreSrcPort, dstIP, coreDstPort, bypassVLAN);
				
				short accessSrcPort = accessSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(srcIP));
				short accessDstPort = accessSwitch.getPortFromMac(
						NetworkTopo.getMacAddress(dstIP));
				IOFSwitch accessSw = floodlightProvider.getSwitches().get(accessSwitch.getId());
				
				pushIPBypass(accessSw, dstIP, accessDstPort, srcIP, accessSrcPort, bypassVLAN);
				pushIPBypassStripVLAN(accessSw, srcIP, accessSrcPort, dstIP, accessDstPort, bypassVLAN);
			}
		}


	}



	/**
	 * push bypass rules to edge switch
	 * @param sw
	 * @param srcIP
	 * @param srcPort
	 * @param dstIP
	 * @param dstPort
	 * @param vlan
	 */
	private void pushIPBypass(IOFSwitch sw,
			int srcIP, short srcPort, int dstIP, short dstPort,
			short vlan) {
		// Read in packet data headers by using an OFMatch structure
		OFMatch match = new OFMatch();
		match.setWildcards(~
				(
						OFMatch.OFPFW_IN_PORT |
						OFMatch.OFPFW_DL_TYPE |
						OFMatch.OFPFW_NW_SRC_MASK |
						OFMatch.OFPFW_NW_DST_MASK
						)
				);
		match.setInputPort(srcPort);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(srcIP);
		match.setNetworkDestination(dstIP);

		OFFlowMod rule = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		rule.setCommand(OFFlowMod.OFPFC_ADD);
		rule.setMatch(match);
		rule.setPriority((short) 128);

		// specify timers for the life of the rule
		rule.setIdleTimeout((short) 0);
		// 		rule.setIdleTimeout(LearningSwitch.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		rule.setHardTimeout(LearningSwitch.FLOWMOD_DEFAULT_HARD_TIMEOUT);

		// set the buffer id to NONE - implementation artifact
		rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);

		// set of actions to apply to this rule
		ArrayList<OFAction> actions = new ArrayList<OFAction>();

		OFActionVirtualLanIdentifier vlanTag =
				new OFActionVirtualLanIdentifier();
		vlanTag.setVirtualLanIdentifier(vlan);
		actions.add(vlanTag);

		OFAction outputTo = new OFActionOutput(dstPort);
		actions.add(outputTo);

		rule.setActions(actions);

		// specify the length of the flow structure created
		rule.setLengthU(OFFlowMod.MINIMUM_LENGTH + 
				OFActionVirtualLanIdentifier.MINIMUM_LENGTH +
				OFActionOutput.MINIMUM_LENGTH);

		try {
			sw.write(rule, null);
			sw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void pushIPBypassStripVLAN(IOFSwitch sw,
			int srcIP, short srcPort, int dstIP, short dstPort,
			short vlanToStrip) {
		// Read in packet data headers by using an OFMatch structure
		OFMatch match = new OFMatch();
		match.setWildcards(~
				(
						OFMatch.OFPFW_IN_PORT |
						OFMatch.OFPFW_DL_TYPE |
						OFMatch.OFPFW_DL_VLAN |
						OFMatch.OFPFW_NW_SRC_MASK |
						OFMatch.OFPFW_NW_DST_MASK
						)
				);
		match.setInputPort(srcPort);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setDataLayerVirtualLan(vlanToStrip);
		match.setNetworkSource(srcIP);
		match.setNetworkDestination(dstIP);

		OFFlowMod rule = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		rule.setCommand(OFFlowMod.OFPFC_ADD);
		rule.setMatch(match);
		rule.setPriority((short) 128);
		
		// specify timers for the life of the rule
		rule.setIdleTimeout((short) 0);
		// 		rule.setIdleTimeout(LearningSwitch.FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		rule.setHardTimeout(LearningSwitch.FLOWMOD_DEFAULT_HARD_TIMEOUT);

		// set the buffer id to NONE - implementation artifact
		rule.setBufferId(OFPacketOut.BUFFER_ID_NONE);

		// set of actions to apply to this rule
		ArrayList<OFAction> actions = new ArrayList<OFAction>();

		OFActionStripVirtualLan vlanStrip =
				new OFActionStripVirtualLan();
		actions.add(vlanStrip);
		
		OFAction outputTo = new OFActionOutput(dstPort);
		actions.add(outputTo);

		rule.setActions(actions);

		// specify the length of the flow structure created
		rule.setLengthU(OFFlowMod.MINIMUM_LENGTH + 
				OFActionStripVirtualLan.MINIMUM_LENGTH +
				OFActionOutput.MINIMUM_LENGTH);

		try {
			sw.write(rule, null);
			sw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
