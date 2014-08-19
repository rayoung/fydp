/************************************************************************************
 *
 *  Copyright (C) 2009-2011 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/
package com.QSK.bleProfiles;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import com.broadcom.bt.le.api.BleClientProfile;
import com.broadcom.bt.le.api.BleClientService;
import com.broadcom.bt.le.api.BleCharacteristic;
import com.broadcom.bt.le.api.BleGattID;

public class HelloBLEProfileClient extends BleClientProfile {
    private static String TAG = "HelloBLEProfileClient";

    private static final BleGattID myUuid = new BleGattID("5ab20001-b355-4d8a-96ef-2963812dd0b8");
    private static final BleGattID USER_NAME_CHARACTERISTIC = new BleGattID("5ab20002-b355-4d8a-96ef-2963812dd0b8");
    
    public static final String HELLOBLE_CONNECTED = "com.broadcom.action.helloble_connected";
    public static final String HELLOBLE_DISCONNECTED = "com.broadcom.action.findme_disconnected";
    public static final String HELLOBLE_REGISTERED = "com.broadcom.action.findme_registered";

	private HelloBleServiceClient mHelloBleService = null;
    private Context mContext = null;
    
    public HelloBLEProfileClient(Context context) {
        super(context, myUuid);
        mContext = context;
        
        mHelloBleService = new HelloBleServiceClient(context);

        Log.d(TAG, "HelloBLEProfileClient");

        ArrayList<BleClientService> services = new ArrayList<BleClientService>();
        services.add(mHelloBleService);
        
        init(services, null);
    }
    
    public synchronized void deregister() throws InterruptedException {
    	deregisterProfile();
		wait(5000);
    }
    
    public void setUserName(BluetoothDevice device) {
        BleCharacteristic userNameCharacteristic = 
       		mHelloBleService.getCharacteristic(device, USER_NAME_CHARACTERISTIC);
		
        if(device != null && userNameCharacteristic != null)
        {
        	byte[] value = {'t', 'a', 'u', 's', 'e', 'e', 'f'};
        	userNameCharacteristic.setValue(value);
        	mHelloBleService.writeCharacteristic(device, 0, userNameCharacteristic);
        }
	}
    
    public String getLocalUserName(BluetoothDevice device) throws UnsupportedEncodingException
    {
    	BleCharacteristic userNameCharacteristic = 
           		mHelloBleService.getCharacteristic(device, USER_NAME_CHARACTERISTIC);
    	    	
    	int length = userNameCharacteristic.getLength();
    	
    	String temp2 = null;
    	
    	if(length > 0)
    	{
    		byte[] temp = userNameCharacteristic.getValue();
    		temp2 = new String(temp, "US-ASCII");
    	}
    	
    	return temp2;
    }

	public void getUserName(BluetoothDevice device) {
        BleCharacteristic userNameCharacteristic = 
       		mHelloBleService.getCharacteristic(device, USER_NAME_CHARACTERISTIC);
		
        if(device != null && userNameCharacteristic != null)
        	mHelloBleService.readCharacteristic(device, userNameCharacteristic);
	}
    
    public void onInitialized(boolean success) {
        Log.d(TAG, "onInitialized");
        if (success) {
        	registerProfile();
        }
    }

    public void onRefreshed(BluetoothDevice device) {
        Log.d(TAG, "onRefreshed");
    }

    public void onProfileRegistered() {
    	Log.d(TAG, "onProfileRegistered");
        
        Intent intent = new Intent();
        intent.setAction(HELLOBLE_REGISTERED);
        mContext.sendBroadcast(intent);
    }

    public void onProfileDeregistered() {
        Log.d(TAG, "onProfileDeregistered");
        notifyAll();
    }
    
    public void onDeviceConnected(BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnected");
        
        Intent intent = new Intent();
        intent.setAction(HELLOBLE_CONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
        mContext.sendBroadcast(intent);
        refresh(device);
    }
    
    public void onDeviceDisconnected(BluetoothDevice device) {
        Log.d(TAG, "onDeviceDisconnected");
        
        Intent intent = new Intent();
        intent.setAction(HELLOBLE_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
        mContext.sendBroadcast(intent);
    }
}
