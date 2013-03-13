package com.ebarch.ipgamepad;

import java.io.IOException;
import java.net.DatagramPacket;

import android.util.Log;

/* The main networking thread that sends the joystick UDP packets */
class NetworkingThread extends Thread {
    /**
	 * 
	 */
	protected final IPGamepad ipGamepad;

	/**
	 * @param ipGamepad
	 */
	NetworkingThread(IPGamepad ipGamepad) {
		this.ipGamepad = ipGamepad;
	}

	protected volatile boolean stop = false;

    public void run() {
            while (!stop) {
            	if (this.ipGamepad.leftActive || this.ipGamepad.rightActive) {
            		// Robot is enabled - let's send some data
		    		try {
		    			// A packet contains 5 bytes - leftJoystickY, leftJoystickX, rightJoystickY, rightJoystickX, Aux Byte
		    			byte auxByte;
		    			
		    			// Aux byte can be used for things you'd like to enable/disable on a robot such as headlights or relays
		    			if (this.ipGamepad.auxbyte)
		    				auxByte = (byte) 255;
		    			else
		    				auxByte = (byte) 0;
		    			
						byte[] buf = new byte[] { IPGamepad.mapJoystick(this.ipGamepad.leftY), IPGamepad.mapJoystick(this.ipGamepad.leftX), IPGamepad.mapJoystick(this.ipGamepad.rightY), IPGamepad.mapJoystick(this.ipGamepad.rightX), auxByte };
						DatagramPacket p = new DatagramPacket(buf, buf.length, this.ipGamepad.ipAddress, this.ipGamepad.port);
						this.ipGamepad.udpSocket.send(p);
					} catch (Exception e) {}
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
            	

            }
    }

    public synchronized void requestStop() {
            stop = true;
    }
}