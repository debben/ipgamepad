package com.ebarch.ipgamepad;

public class PacketStruct {

	//put fields here. Be sure to specify type
	int throttle; //16 bit signed
	int steering; //16 bit signed
	
	public PacketStruct(){};
	
	public byte[] writeObject(){
		byte[] retVal = new byte[4];
		
		//do not use big endian. Chip kit uses little endian.
		//Yeah it's a bitch, but it's better we do a bit of 
		//work here in JavaLand so we save time on the kit.
		
		//pack throttle
		retVal[1] = (byte) ((throttle & 0xFF00) >> 8);
		retVal[0] = (byte) ((throttle & 0xFF));
		
		//pack steering
		retVal[3] = (byte) ((steering & 0xFF00) >> 8);
		retVal[2] = (byte) ((steering & 0xFF));
		return retVal;
	}
}
