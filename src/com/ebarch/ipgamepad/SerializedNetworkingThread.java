package com.ebarch.ipgamepad;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.util.Log;

public class SerializedNetworkingThread extends NetworkingThread {

	SerializedNetworkingThread(DriverPad driverPad) {
		super(driverPad);
	}
	
	byte[] buf = new byte[10];
	@Override
	public void run() {
        while (!stop) {
        	if (((DriverPad)this.ipGamepad).enabled) {
        		// Robot is enabled - let's send some data
	    		try {
	    			// A packet contains 5 bytes - leftJoystickY, leftJoystickX, rightJoystickY, rightJoystickX, Aux Byte
	    			byte auxByte;
	    			
	    			// Aux byte can be used for things you'd like to enable/disable on a robot such as headlights or relays
	    			if (this.ipGamepad.auxbyte)
	    				auxByte = (byte) 255;
	    			else
	    				auxByte = (byte) 0;
	    			
					//byte[] buf = new byte[] { IPGamepad.mapJoystick(this.ipGamepad.leftY), IPGamepad.mapJoystick(this.ipGamepad.leftX), IPGamepad.mapJoystick(this.ipGamepad.rightY), IPGamepad.mapJoystick(this.ipGamepad.rightX), auxByte };
	    			byte[] buf = ((DriverPad)this.ipGamepad).packetStruct.writeObject();
					DatagramPacket p = new DatagramPacket(buf, buf.length, this.ipGamepad.ipAddress, this.ipGamepad.port);
					this.ipGamepad.udpSocket.send(p);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					Thread.sleep(this.ipGamepad.packetRate);
				}
				catch (InterruptedException e) {}
    		}
    		else {
    			// Robot is disabled - wait a little bit before trying again
    			try {
					Thread.sleep(this.ipGamepad.packetRate);
				}
				catch (InterruptedException e) {}
    		}
        	
        	DatagramPacket packet = new DatagramPacket(buf, buf.length);
        	try {
				this.ipGamepad.udpSocket.receive(packet);
				ByteBuffer bb = ByteBuffer.wrap(buf);
				//let's do some handling.
				bb.order(ByteOrder.LITTLE_ENDIAN);
				this.ipGamepad.odometer = bb.getInt();
				this.ipGamepad.period = bb.getInt();
				
				this.ipGamepad.updateOdometry();
				//let's log the output of this packet
				
				
			} catch (IOException e) {
				//Log.i(DriverPad.LOG, e.getMessage());
			}
        }
	}

}
