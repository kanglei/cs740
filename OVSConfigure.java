package net.floodlightcontroller.zkz.learningswitch;

import java.util.HashSet;
import java.util.Set;

public class OVSConfigure {
	private static long ovs[] = {
		0x01, 0x101, 0x10201
//		0x101, 0x201
//		0x01, 0x10101, 0x10201
	};
	
	private static Set<Long> ovsSwitchSet;

	static {
		ovsSwitchSet = new HashSet<Long>();
		for(int i = 0; i < ovs.length; ++i) {
			ovsSwitchSet.add(ovs[i]);
		}
	}
	
	public static boolean isOpenVSwitch(long id) {
		return ovsSwitchSet.contains(id);
	}
}
