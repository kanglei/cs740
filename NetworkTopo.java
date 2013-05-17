package net.floodlightcontroller.zkz.learningswitch;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;


public class NetworkTopo {
	private static Map<Integer, Long> IPToMACMap
					= new HashMap<Integer, Long>();
	
	public static void configureTopo(IFloodlightProviderService floodlightProvider,
									 ILinkDiscoveryService linkDiscoveryProvider,
									 IDeviceService deviceProvider) {
		
		Collection<? extends IDevice> devices = deviceProvider.getAllDevices();
		Iterator<? extends IDevice> deviceIterator = devices.iterator();
		while (deviceIterator.hasNext()) {
			IDevice device = deviceIterator.next();

			if (device.getIPv4Addresses().length > 0) {
				System.err.print("device " + device.getDeviceKey() + ": ip = ");

				for (int ip : device.getIPv4Addresses()) {
					IPToMACMap.put(ip, device.getMACAddress());
					
					for (int j = 0; j < 4; j++) {
						System.err.print((ip >> 24) & 0xFF);
						if (j < 3)
							System.err.print(",");
						ip <<= 8;
					}
					System.err.print(" ");
				}
				System.err.print(" mac = " + device.getMACAddressString());
				System.err.print(" switch = ");
				for (SwitchPort sp : device.getAttachmentPoints()) {
					System.err.print(sp.getSwitchDPID() + ", " + sp.getPort());
				}
				System.err.println();
			}
		}
	}
	
	public static Long getMacAddress(int ip) {
		return IPToMACMap.get(ip);
	}
}
