package com.ebarch.ipgamepad;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.ebarch.ipgamepad.R.id;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.content.Intent;
import android.content.SharedPreferences;


public class IPGamepad extends Activity {
    
    protected SharedPreferences preferences;
    
    protected NetworkingThread networkThread;
    protected DatagramSocket udpSocket;
    InetAddress ipAddress;
    int packetRate;
    int port;
    boolean auxbyte;
    boolean leftActive;

	boolean rightActive = false;
    protected int leftX;

	protected int leftY;

	protected int rightX;

	protected int rightY = 0;
	
	private DualJoystickView joystick;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        
        joystick = (DualJoystickView)findViewById(R.id.dualjoystickView);
        joystick.setOnJostickMovedListener(_listenerLeft, _listenerRight);
        
        // Initialize preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Setup the networking
        try {
        	udpSocket = new DatagramSocket();
        	updateNetworking();
        }
        catch (Exception e) {
        	// Networking exception
        }
    }
    
    /* Call this whenever the network settings need to be reloaded */
    public void updateNetworking() {
    	try {
			ipAddress = InetAddress.getByName(preferences.getString("ipaddress", "192.168.1.22"));
			port = Integer.parseInt(preferences.getString("port", "4444"));
			packetRate = Integer.parseInt(preferences.getString("txinterval", "20"));
    	} catch (UnknownHostException e) {
    		// Networking exception
    	}
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {	
		startActivity(new Intent(this, Preferences.class));		
		return true;
	}
	
	private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

		public void OnMoved(int pan, int tilt) {
			leftX = pan;
			leftY = tilt;
			leftActive = true;
		}

		public void OnReleased() {
			leftActive = false;
		}
		
		public void OnReturnedToCenter() {
			leftActive = false;
		};
	};
	
    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

    	
		public void OnMoved(int pan, int tilt) {
    		rightX = pan;
			rightY = tilt;
			rightActive = true;
		}

		
		public void OnReleased() {
			rightActive = false;
		}
		
		public void OnReturnedToCenter() {
			rightActive = false;
		};
	};
    
    static byte mapJoystick(int input) {
    	int result = (int)mapValue((double)input, -150, 150, 0, 255);
    	
    	if (result < 0)
    		result = 0;
    	else if (result > 255)
    		result = 255;
    	
    	return (byte)result;
    }
    
    public static double mapValue(double input, double inMin, double inMax, double outMin, double outMax) {
    	return (input - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
    }
    
    /* Call this to start the main networking thread */
    public synchronized void startNetworkingThread(){
        if(networkThread == null){       
                networkThread = new NetworkingThread(this);
                networkThread.start();
        }
    }
    
    /* Call this to stop the main networking thread */
    public synchronized void stopNetworkingThread(){
        if(networkThread != null){
                networkThread.requestStop();
                networkThread = null;
        }
    }
    
    @Override
    protected void onPause() {
    	// End Ethernet communications
    	stopNetworkingThread();
    	
    	super.onPause();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	// Update networking settings
    	updateNetworking();
    	
    	// Update headlight status
    	auxbyte = preferences.getBoolean("auxbyte", false);
    	
    	// Begin Ethernet communications
    	startNetworkingThread();
    }
}