import socket 
UDP_IP = "192.168.0.75"
UDP_PORT = 4444
 
sock = socket.socket(socket.AF_INET, # Internet
                     socket.SOCK_DGRAM) # UDP
sock.bind((UDP_IP, UDP_PORT))
 
while True:
     data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes     
     print("Throttle: %" , int.from_bytes(data[0:2], byteorder='big', signed=True)/10)
     print("\nSteering: %" , int.from_bytes(data[2:4], byteorder='big' , signed=True)/10)