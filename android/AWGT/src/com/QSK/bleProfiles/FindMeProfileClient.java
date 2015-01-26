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

import java.util.ArrayList;

import com.broadcom.bt.le.api.BleClientProfile;
import com.broadcom.bt.le.api.BleClientService;
import com.broadcom.bt.le.api.BleCharacteristic;
import com.broadcom.bt.le.api.BleGattID;

public class FindMeProfileClient extends BleClientProfile {
    private static String TAG = "FindMeProfileClient";

    public static final String FINDME_CONNECTED = "com.broadcom.action.findme_connected";
    public static final String FINDME_DISCONNECTED = "com.broadcom.action.findme_disconnected";
    public static final String FINDME_REGISTERED = "com.broadcom.action.findme_registered";

    public final static byte ALERT_LEVEL_NONE = 0;
    public final static byte ALERT_LEVEL_LOW = 1;
    public final static byte ALERT_LEVEL_HIGH = 2;

    private static final BleGattID myUuid = new BleGattID("00001802-1112-2223-8000-00805f9b34fb");
    private static final BleGattID ALERT_LEVEL_CHARACTERISTIC = new BleGattID("00002a06-0000-1000-8000-00805f9b34fb");

	private ImmediateAlertServiceClient mImmediateAlertService = new ImmediateAlertServiceClient();
    private Context mContext = null;
    
    public FindMeProfileClient(Context context) {
        super(context, myUuid);
        mContext = context;

        Log.d(TAG, "FindMeProfileClient");

        ArrayList<BleClientService> services = new ArrayList<BleClientService>();
        services.add(mImmediateAlertService);
        
        init(services, null);
    }
    
    public synchronized void deregister() throws InterruptedException {
    	deregisterProfile();
		wait(5000);
    }

	public void alert(BluetoothDevice device, byte alertLevel) {
        BleCharacteristic alertLevelCharacteristic = 
                    mImmediateAlertService.getCharacteristic(device, ALERT_LEVEL_CHARACTERISTIC);

		byte value = alertLevel; //{ FindMeProfileClient.ALERT_LEVEL_HIGH };
		
		alertLevelCharacteristic.setValue(value);

		mImmediateAlertService.writeCharacteristic(device, 0, alertLevelCharacteristic);
	}
    
    public void onInitialized(boolean success) {
        Log.d(TAG, "onInitialized");
        if (success) {
        	registerProfile();
        }
    }

    public void onDeviceConnected(BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnected");        
        refresh(device);
    }

    public void onDeviceDisconnected(BluetoothDevice device) {
        Log.d(TAG, "onDeviceDisconnected");
        
        Intent intent = new Intent();
        intent.setAction(FINDME_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
        mContext.sendBroadcast(intent);
    }

    public void onRefreshed(BluetoothDevice device) {
        Log.d(TAG, "onRefreshed");
        
        Intent intent = new Intent();
        intent.setAction(FINDME_CONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device.getAddress());
        mContext.sendBroadcast(intent);
    }

    public void onProfileRegistered() {
        Log.d(TAG, "onProfileRegistered");
        
        Intent intent = new Intent();
        intent.setAction(FINDME_REGISTERED);
        mContext.sendBroadcast(intent);
    }

    public void onProfileDeregistered() {
        Log.d(TAG, "onProfileDeregistered");
        notifyAll();
    }
}
