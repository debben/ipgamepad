import socket 
import time
import struct
UDP_IP = "192.168.0.102"
UDP_PORT = 44400
 
sock = socket.socket(socket.AF_INET, # Internet
                     socket.SOCK_DGRAM) # UDP
sock.bind((UDP_IP, UDP_PORT))
lastMillis =0
millis=0
while True:	 	 
     data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes     
     mil =int(round(time.time()*1000))
     millis = mil	- lastMillis
     print("got packet at " + str(millis))    
     lastMillis = mil
     sock.sendto("00000000",addr)
     #test = data[0:2]         
     print "Throttle: %" , struct.unpack('<h',data[0:2])[0]/10
     print "\nSteering: %" , struct.unpack('<h',data[2:4])[0]/10
