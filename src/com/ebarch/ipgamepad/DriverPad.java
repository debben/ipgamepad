package com.ebarch.ipgamepad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.BitSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.ebarch.ipgamepad.R.id;
import com.ebarch.ipgamepad.R.layout;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class DriverPad extends IPGamepad implements SensorEventListener, OnSeekBarChangeListener
{
	private Handler mHandler = new Handler(); 
	private SensorManager mSensorManager;
	
	private TextView throtle;
	private TextView steering;
	
	private SeekBar z_max;
	private SeekBar y_max;
	private SeekBar z_buf;
	private SeekBar y_buf;
	
	private String LOG = "AUTONOMOUS";
	
	private Sensor acc;
	private float[] gravity = new float[3];
	private float[] linear_acceleration = new float[3];
	
	protected PacketStruct packetStruct;
	
	private WifiManager wifiManage;
	boolean mExternalStorageAvailable = false;
	boolean mExternalStorageWriteable = false;
	String state = Environment.getExternalStorageState();
	
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        // Initialize preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
 
        	
        	setContentView(getLayoutInflater().inflate(R.layout.driver_view,null));        	

        
        
        z_max = (SeekBar) findViewById(id.z_max);
        y_max = (SeekBar) findViewById(id.y_max);
        z_buf = (SeekBar) findViewById(id.z_buff);
        y_buf = (SeekBar) findViewById(id.y_buff);
    	
		//set default values
        z_max.setProgress(preferences.getInt("z_max", 1000));
        y_max.setProgress(preferences.getInt("y_max", 1000));
        z_buf.setProgress(preferences.getInt("z_buf", 0));
        y_buf.setProgress(preferences.getInt("y_buf", 0));
        
        //configure listeners       
        z_max.setOnSeekBarChangeListener(this);
        y_max.setOnSeekBarChangeListener(this);
        z_buf.setOnSeekBarChangeListener(this);
        y_buf.setOnSeekBarChangeListener(this);
        
        //locate controls
        throtle = (TextView) findViewById(id.throtle_text);
        steering = (TextView) findViewById(id.steering_text);
        //setup wifi settings
        packetStruct = new PacketStruct();
        wifiManage = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                
		
        // Setup the networking
        try {
        	udpSocket = new DatagramSocket();
        	updateNetworking();
        }
        catch (Exception e) {
        	// Networking exception
        }
    }

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

    public synchronized void startNetworkingThread(){
        if(networkThread == null){       
                networkThread = new SerializedNetworkingThread(this);
                networkThread.start();
        }
    }
	
	public void onSensorChanged(SensorEvent event) {
		// In this example, alpha is calculated as t / (t + dT),
		  // where t is the low-pass filter's time-constant and
		  // dT is the event delivery rate.

		  final float alpha = (float) 0.8;

		  // Isolate the force of gravity with the low-pass filter.
		  gravity[0] = event.values[0];//alpha * gravity[0] + (1 - alpha) * event.values[0];
		  gravity[1] = event.values[1];//alpha * gravity[1] + (1 - alpha) * event.values[1];
		  gravity[2] = event.values[2];//alpha * gravity[2] + (1 - alpha) * event.values[2];

		  // Remove the gravity contribution with the high-pass filter.
		  linear_acceleration[0] = event.values[0] - gravity[0];
		  linear_acceleration[1] = event.values[1] - gravity[1];
		  linear_acceleration[2] = event.values[2] - gravity[2];
		  
		  
		  mHandler.post(updateUI);
	}
    
	  Runnable updateUI = new Runnable() {
			
		public void run() {
			//acc_x.setText("x: " + ((int) (100 * gravity[0])));
			//acc_y.setText("y: " + (int) (100 * gravity[1]));
			//acc_z.setText("z: " + (int) (100 * gravity[2]));
			
			packetStruct.throttle = (int) (100 * gravity[2]);
			packetStruct.steering = (int) (100 * gravity[1]);
			
			if(packetStruct.throttle <= z_buf.getProgress() && packetStruct.throttle >= 0-z_buf.getProgress()){
				packetStruct.throttle =0;
			}
			else if(packetStruct.throttle >= z_max.getProgress() || packetStruct.throttle <= 0-z_max.getProgress()){
				packetStruct.throttle = 1000;
			}
			else //scale
			{
				int temp=0;
				if(packetStruct.throttle>0){
					temp = packetStruct.throttle - z_buf.getProgress();					
				}
				else{
					temp = packetStruct.throttle + z_buf.getProgress();
				}
				temp *= z_max.getProgress() - z_buf.getProgress();
				temp /= 1000;
				packetStruct.throttle = temp;
			}
			
			
			
			if(packetStruct.steering <= y_buf.getProgress() && packetStruct.steering >= 0-y_buf.getProgress()){
				packetStruct.steering =0;
			}
			else if(packetStruct.steering >= y_max.getProgress() || packetStruct.steering <= 0-y_max.getProgress()){
				packetStruct.steering = 1000;
			}
			else //scale
			{
				int temp=0;
				if(packetStruct.steering>0){
					temp = packetStruct.steering - y_buf.getProgress();					
				}
				else{
					temp = packetStruct.steering + y_buf.getProgress();
				}
				temp *= y_max.getProgress() - y_buf.getProgress();
				temp /= 1000;
				packetStruct.steering = temp;
			}
			
			DriverPad.this.throtle.setText("Throtle: " + packetStruct.throttle/10 + "%");
			DriverPad.this.steering.setText("steering: " + packetStruct.steering/10 + "%");
						
		}
	};
	protected String preSharedKey;
	protected JSONObject flattened;
	protected FileWriter output;
	public boolean enabled = false;
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == id.preferences)
			startActivity(new Intent(this, Preferences.class));
		else if(item.getItemId() == id.WifiMenu)
			writeWifiPreferences();
		else if(item.getItemId() == id.toggle_enable){
			enabled = !enabled;
		}
		return true;
	}
	
	private void checkSDCard(){
		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    // We can read and write the media
		    mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		    // We can only read the media
		    mExternalStorageAvailable = true;
		    mExternalStorageWriteable = false;
		} else {
		    // Something else is wrong. It may be one of many other states, but all we need
		    //  to know is we can neither read nor write
		    mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
	}
	
	private void showWPAInput(){
		//fetch the actual key
		AlertDialog.Builder build = new AlertDialog.Builder(this);
		final View custom = getLayoutInflater().inflate(layout.wifi_code, (ViewGroup) getCurrentFocus());
		build.setView(custom);
		build.setTitle("Input Needed");
		build.setNegativeButton("Cancel", new OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();								
			}
		});
		
		build.setPositiveButton("Set", new OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {								
				//TODO input validation
				EditText pass = (EditText)custom.findViewById(id.wpa_password);
				if(pass != null){
					preSharedKey = pass.getText().toString();											
				}
				setPasswordandSave();
				dialog.dismiss();
			}
		});

		build.setCancelable(true);

		
		AlertDialog passwordGet = build.create();
		passwordGet.show();
	}
	
	protected void setPasswordandSave() {
		try {
			flattened.put("preSharedKey", preSharedKey);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		if(output != null){
			//now let's write this down
			try {
				output.append(flattened.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		Log.i(LOG, flattened.toString());
		
		if(output != null ){
			try {
				output.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//warn user about unmounting sdcard
		Toast.makeText(this, "Power down phone before removing the SD card!", Toast.LENGTH_LONG).show();
	}

	private void writeWifiPreferences() {
		// first check that we even have an SD Card inserted
		if(!mExternalStorageAvailable || !mExternalStorageWriteable){
			//toast that we don't have an sdcard mounted
			Toast.makeText(this, "No SD card!", Toast.LENGTH_SHORT).show();
			return;
		}
		
		//now let's get the file ready to save
		File saveTo = new File(getExternalFilesDir(null),"network.config");
		File pathCheck = new File(Environment.getExternalStorageDirectory().getParent(),"extSdCard");
		if(pathCheck.exists() && pathCheck.isDirectory()){//stupid hack for newer phones with emulated storage
			saveTo = new File(saveTo.getAbsoluteFile().toString().replace(Environment.getExternalStorageDirectory().getName(), "extSdCard"));
		}
		if(!saveTo.exists()){
			try {
				saveTo.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		output = null;
		try {
			output = new FileWriter(saveTo);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		boolean complete = true;
		
		WifiInfo info = wifiManage.getConnectionInfo();
		Log.i(LOG, "Wifi network name: " + info.getSSID());
		Log.i(LOG, "Current connection.toString(): " + info.toString());
		
		//now let's try finding that network's configuration in the config manager
		for(WifiConfiguration config : wifiManage.getConfiguredNetworks()){
			if(info.getNetworkId() == config.networkId){
				flattened = new JSONObject();
				try {
					flattened.put("preferedIP",preferences.getString("ipaddress", "192.168.0.110"));
					flattened.put("port",Integer.parseInt(preferences.getString("port", "4444")));
					flattened.put("BSSID", config.BSSID);
					flattened.put("SSID", config.SSID.substring(1, config.SSID.length()-1));
					flattened.put("allowedAuthAlgorithms", bitSetToInt(config.allowedAuthAlgorithms));
					flattened.put("allowedGroupCiphers", bitSetToInt(config.allowedGroupCiphers));
					flattened.put("allowedKeyManagement", bitSetToInt(config.allowedKeyManagement));
					flattened.put("allowedPairwiseCiphers", bitSetToInt(config.allowedPairwiseCiphers));
					flattened.put("allowedProtocols", bitSetToInt(config.allowedProtocols));
					flattened.put("hiddenSSID", config.hiddenSSID);
					flattened.put("networkID", config.networkId);
					if(config.preSharedKey != null && config.preSharedKey.equals("*")){						
						complete = false;
						showWPAInput();						
					}
					else if(!config.preSharedKey.equals("")){
						preSharedKey = config.preSharedKey.substring(0,config.preSharedKey.length()-1);
					}
					if(config.wepKeys != null && config.wepKeys.length > 0){
						JSONArray a = new JSONArray();
						for(String key : config.wepKeys)
							a.put(key);
						flattened.put("wepKeys", a);
						flattened.put("wepTxKeyIndex", config.wepTxKeyIndex);
					}
					
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
				if(complete)
					setPasswordandSave();
				break;
			}
		}		
	}

	private static int bitSetToInt(BitSet bitSet)
	{
	    int bitInteger = 0;
	    for(int i = 0 ; i < 32; i++)
	        if(bitSet.get(i))
	            bitInteger |= (1 << i);
	    return bitInteger;
	}
	
	
	@Override
	protected void onPause() {
	  super.onPause();
	  mSensorManager.unregisterListener(this);	  
	}
	
	  @Override
	  protected void onResume() {
	    super.onResume();
	    mSensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
	    checkSDCard();
	  }
	  
	protected void onDestroy(){
		super.onDestroy();
		
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		String pref = null;
		switch(seekBar.getId()){
			case id.z_buff:
				pref = "z_buf";
				break;
			case id.y_buff:
				pref = "y_buf";
				break;
			case id.z_max:
				pref = "z_max";
				break;
			case id.y_max:
				pref = "y_max";
				break;
			default:
				return;
		}
		preferences.edit().putInt(pref, progress).commit();
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}
	
}
