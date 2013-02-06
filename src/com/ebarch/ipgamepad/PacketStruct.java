package com.ebarch.ipgamepad;

public class PacketStruct {

	//put fields here. Be sure to specify type
	int throttle; //16 bit signed
	int steering; //16 bit signed
	
	public PacketStruct(){};
	
	public byte[] writeObject(){
		byte[] retVal = new byte[4];
		
		//big endian
		
		//pack throttle
		retVal[0] = (byte) ((throttle & 0xFF00) >> 8);
		retVal[1] = (byte) ((throttle & 0xFF));
		
		//pack steering
		retVal[2] = (byte) ((steering & 0xFF00) >> 8);
		retVal[3] = (byte) ((steering & 0xFF));
		return retVal;
	}
}
