package com.ebarch.ipgamepad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.BitSet;

import org.codeandmagic.android.gauge.GaugeView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.os.PowerManager;

public class DriverPad extends IPGamepad implements SensorEventListener, OnCheckedChangeListener, android.view.View.OnClickListener {
	private Handler mHandler = new Handler(); 
	private SensorManager mSensorManager;
	
	
	public static String LOG = "AUTONOMOUS";
	
	private Sensor acc;
	private float[] gravity = new float[3];
	private float[] linear_acceleration = new float[3];
	
	protected PacketStruct packetStruct;
	
	private WifiManager wifiManage;
	boolean mExternalStorageAvailable = false;
	boolean mExternalStorageWriteable = false;
	String state = Environment.getExternalStorageState();
	
	//load preffs into ints
	int zmax,zbuf,ymax,ybuf;
	private GaugeView mSpeedometer, mTachometer;
	
	private ToggleButton mLeftSig, mRightSig;
	
	TextView unitLabel = null;
	
	Switch stereoPower = null;
	
	Button prev,next;
	
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        // Initialize preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
        	
       	setContentView(getLayoutInflater().inflate(R.layout.driver_view,null));        	

        //setup wifi settings
        packetStruct = new PacketStruct();
        wifiManage = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        //find controls
        mSpeedometer = (GaugeView) findViewById(R.id.speedometer);
        mTachometer = (GaugeView) findViewById(R.id.tachometer);
        
        mLeftSig = (ToggleButton) findViewById(R.id.left_sig);
        mRightSig = (ToggleButton) findViewById(R.id.right_sig);
        
        mLeftSig.setOnCheckedChangeListener(this);
        mRightSig.setOnCheckedChangeListener(this);
        

        unitLabel = (TextView) findViewById(R.id.unitLabel);
        
        stereoPower = (Switch) findViewById(R.id.stereo_select);
        prev = (Button) findViewById(R.id.btnPrev);
        next = (Button) findViewById(R.id.btnNext);
        
        stereoPower.setOnCheckedChangeListener(this);
        prev.setOnClickListener(this);
        next.setOnClickListener(this);
        
        
        zmax = Integer.parseInt(preferences.getString("z_max", "1000"));
        ymax = Integer.parseInt(preferences.getString("y_max", "1000"));
        zbuf = Integer.parseInt(preferences.getString("z_buf", "0"));
        ybuf = Integer.parseInt(preferences.getString("y_buf", "0"));
        
        //wake lock
        pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, LOG);        
        
        
        // Setup the networking
        try {        	
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
			
			if(packetStruct.throttle <= zbuf && packetStruct.throttle >= 0-zbuf){
				packetStruct.throttle =0;
			}
			else if(packetStruct.throttle >= zmax || packetStruct.throttle <= 0-zmax){
				packetStruct.throttle = (packetStruct.throttle < 0 ? -1000 : 1000);
			}
			else //scale
			{
				int temp=0;
				if(packetStruct.throttle>0){
					temp = packetStruct.throttle - zbuf;					
				}
				else{
					temp = packetStruct.throttle + zbuf;
				}
				temp *= zmax - zbuf;
				temp /= 1000;
				packetStruct.throttle = temp;

			}

			
			
			
			if(packetStruct.steering <= ybuf && packetStruct.steering >= 0-ybuf){
				packetStruct.steering =0;
			}
			else if(packetStruct.steering >= ymax || packetStruct.steering <= 0-ymax){
				packetStruct.steering = (packetStruct.steering < 0 ? -1000 : 1000);
			}
			else //scale
			{
				int temp=0;
				if(packetStruct.steering>0){
					temp = packetStruct.steering - ybuf;					
				}
				else{
					temp = packetStruct.steering + ybuf;
				}
				temp *= ymax - ybuf;
				temp /= 1000;
				packetStruct.steering = temp;
			}
			
			//first check for cruise
			if(packetStruct.throttle <=0){
				cruise = 0;
				//TODO: MAKE TOAST ABOUT CRUISE CONTROL.
			}
			if(cruise > 0){
				//for now, just make this a throttle set-point.
				packetStruct.aux |= 0x10;
				
			}
			else{
				packetStruct.aux &= 0xEF;
			}

			if(mTachometer != null){						
				mTachometer.setTargetValue(packetStruct.throttle/10);
			}
		}
	};
	
	int map(int lowIn, int highIn, int lowOut, int highOut, int value)
	{
		return (((highOut - lowOut) * value)/(highIn - lowIn)) + ((highOut-lowOut)/2);
		//packetStruct.steering  = map(ybuf,ymax,-1000,1000,packetStruct.steering);
	}
	
	protected String preSharedKey;
	protected JSONObject flattened;
	protected FileWriter output;
	public boolean enabled = false;
	private int cruise;
	private String unitString;
	protected boolean useFPS;
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == id.preferences)
			startActivity(new Intent(this, Preferences.class));
		else if(item.getItemId() == id.WifiMenu)
			writeWifiPreferences();
		else if(item.getItemId() == id.toggle_enable){
			enabled = !enabled;

		}
		else if(item.getItemId() == id.odom_view){
			//period
			double temp;
			if(useFPS){
				temp = toFeet(odometer);
			}
			else{
				temp = toMiles(odometer);
			}
			
			DecimalFormat d = new DecimalFormat("#.##");
			String msg = String.format("Odometer reads: %s %s", d.format(temp), useFPS ? "feet" : "miles");
			
			Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
					else if(config.preSharedKey != null && !config.preSharedKey.equals("")){
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
	  wl.release();
	  mSensorManager.unregisterListener(this);	  
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event){
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		
		switch(keyCode){
			case KeyEvent.KEYCODE_VOLUME_UP:				
				if(action == KeyEvent.ACTION_DOWN){
					changeCruise(1);
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if(action == KeyEvent.ACTION_DOWN){
					changeCruise(-1);
				}
				return true;
			default:
				return super.dispatchKeyEvent(event);
		}		
	}
	
	  private void changeCruise(int i) {
		cruise += i;
		if(cruise < 0){
			cruise=0;
		}
		//post a toast message about it.
		//Toast t = Toast.makeText(getApplicationContext(), "Cruise changed to " + cruise + " " + unitString, Toast.LENGTH_SHORT);
		//t.show();		
	}

	@Override
	  protected void onResume() {
	    super.onResume();
	    
	    wl.acquire();
	    
	    mSensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
	    checkSDCard();
	    
	    if(preferences != null){
		    //reset params
		    zmax = Integer.parseInt(preferences.getString("z_max", "1000"));
	        ymax = Integer.parseInt(preferences.getString("y_max", "1000"));
	        zbuf = Integer.parseInt(preferences.getString("z_buf", "0"));
	        ybuf = Integer.parseInt(preferences.getString("y_buf", "0"));
	        unitString = preferences.getString("units", "ft/s");
	        String[] units = getResources().getStringArray(R.array.units);	        
	        useFPS = unitString.equals(units[0]);

	        if(unitLabel != null){
	        	unitLabel.setText(useFPS ? units[0] : units[1]);
	        }
	        
	        
	        //if(!useFPS){
	        //	l.removeView(mSpeedometer);	       
	        //	LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
	        //	mSpeedometer = new GaugeView(this,null,R.style.MPH_Speedometer);
	        //	l.addView(mSpeedometer);
	        //}
	        //else
	        //{
	        //	l.removeView(mSpeedometer);	       
	        //	LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
	        //	mSpeedometer = new GaugeView(this,null,R.style.MPH_Speedometer);
	        //	l.addView(mSpeedometer);
	        //}
	        
	    }
	    
	    
	  }
	  
	protected void onDestroy(){
		super.onDestroy();
		
	}

	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView.getId() == R.id.right_sig){
			if(isChecked){
				packetStruct.aux |= 0x01;			
			}
			else{
				packetStruct.aux &= 0xFE;
			}
		}
		else if(buttonView.getId() == R.id.left_sig){
			if(isChecked){
				packetStruct.aux |= 0x02;			
			}
			else{
				packetStruct.aux &= 0xFD;
			}
		}
		else if(buttonView.getId() == R.id.stereo_select){
			if(isChecked){
				packetStruct.aux |= 0x80;
			}
			else
			{
				packetStruct.aux &= 0x7F;
			}
		}
		
	}
	
	public double toFPS(int microsecondPeriod){
		double temp = microsecondPeriod / 1000000.0;
		return  0.669959 / temp;
	}
	
	public double toMPH(int microsecondPeriod){
		double temp = microsecondPeriod / 1000000.0; // to seconds
		return 0.4567896 / temp; ////((3500* miles/rev) over seconds )
	}
	
	public double toFeet(int revolutions)
	{
		return 0.669959 * revolutions;
	}
	
	public double toMiles(int revolutions){
		return revolutions * 0.000126886;
	}
	
	public void updateOdometry() {
		mHandler.post(new Runnable() {
			
			


			public void run() {
				double temp;
				 if(useFPS){
					 temp = toFPS(period);
				 }
				 else{
					 temp = toMPH(period);
				 }
				
				if(mSpeedometer != null && enabled){

					mSpeedometer.setTargetValue((float)temp);
				}
				else
				{
					mSpeedometer.setTargetValue((float)0.0);
				}
				//if(unitLabel != null){
				//	unitLabel.setText(Integer.toString(period));
				//}
			}
		});
	}

	public void onClick(View v) {
		if(v.getId() == R.id.btnPrev){
			packetStruct.aux |= 0x40;
		}
		else if(v.getId() == R.id.btnNext){
			packetStruct.aux |= 0x20;
		}
		
	}
	
}
