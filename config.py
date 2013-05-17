import http.client
import json

class IPPusher(object):

    def __init__(self, server):
        self.server = server

    def get(self, data):
        ret = self.rest_call({}, 'GET')
        return json.loads(ret[2])

    def set(self, data):
        ret = self.rest_call(data, 'POST')
        return ret[0] == 200

    def remove(self, data):
        ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200

    def rest_call(self, data, action):
        path = '/wm/zkz/post/json'
        headers = {
            'Content-type': 'application/text',
            'Accept': 'application/text',
            }
        
        body = data
        conn = http.client.HTTPConnection(self.server, 8080)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        print (ret)
        conn.close()
        return ret


pusher = IPPusher('192.168.56.1')

enableIP1 = '10.0.0.2,10.0.0.4'
pusher.set(enableIP1) 

#enableIP2 = '10.0.0.2,10.0.0.5'
#pusher.set(enableIP2) 
