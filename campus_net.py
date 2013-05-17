#!/usr/bin/python

from mininet.topo import Topo
from mininet.net  import Mininet
from mininet.util import dumpNodeConnections
from mininet.log  import setLogLevel, info
from mininet.node import Controller, RemoteController
from mininet.node import Switch, OVSSwitch, UserSwitch
from mininet.cli  import CLI


class CampusTopo(Topo):

	def __init__(self, **opts):
		Topo.__init__(self, **opts)

		numEdge = 2
		numAccessPerEdge = 2

		coreSwitch = {}
		coreSwitch['sid'] = 1
		coreSwitch['name'] = 's' + str(coreSwitch['sid'])
		coreSwitch['children'] = []

		for i in range(1, numEdge+1) :
			edgeSwitch = {}
			sid = coreSwitch['sid'] + (0x100 * i)
			edgeSwitch['sid'] = sid
			edgeSwitch['name'] = 's' + str(sid)
			edgeSwitch['children'] = []
			coreSwitch['children'].append(edgeSwitch)

			for j in range(1, numAccessPerEdge+1) :
				accessSwitch = {}
				sid = edgeSwitch['sid'] + (0x10000 * j)
				accessSwitch['sid'] = sid
				accessSwitch['name'] = 's' + str(sid)
				accessSwitch['children'] = ['h' + str(i) + str(j)]
				edgeSwitch['children'].append(accessSwitch)

		self.createTopo(coreSwitch)
	
	def createTopo(self, nodeConfig):
		if (isinstance(nodeConfig, str)) :
			return self.addHost(nodeConfig)
		else :
			node = self.addSwitch(nodeConfig['name'])
			for subNodeConfig in nodeConfig['children'] :
				subNode = self.createTopo(subNodeConfig)
				self.addLink(node, subNode)
			return node
		return


class FloodlightRemoteController(RemoteController):
	def __init__(self, name, ip = '192.168.56.1', port = 6633, **opt):
		RemoteController.__init__(self, name, ip, port, **opt)


def initNetwork():
	"Create network"
	net = Mininet(topo = CampusTopo(), ipBase = '10.0.0.0/8',
				  controller = FloodlightRemoteController)

	net.start()
	CLI(net)

if __name__ == '__main__':
	# Tell mininet to print useful information
	setLogLevel('info')
	initNetwork()

