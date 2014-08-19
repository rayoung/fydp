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

import com.broadcom.bt.le.api.BleCharacteristic;
import com.broadcom.bt.le.api.BleClientService;
import com.broadcom.bt.le.api.BleGattID;

public class HelloBleServiceClient extends BleClientService {
    public static String TAG = "HelloBleServiceClient";
    public static final String HELLOBLE_NAME = "com.broadcom.action.helloble_name";
    
    static public BleGattID myUuid = new BleGattID("5ab20001-b355-4d8a-96ef-2963812dd0b8");
    private static final BleGattID USER_NAME_CHARACTERISTIC = new BleGattID("5ab20002-b355-4d8a-96ef-2963812dd0b8");
    
    Context mContext;
    
    public HelloBleServiceClient(Context context) {
        super(myUuid);
        mContext = context;
        Log.d(TAG, "HelloBleServiceClient");
    }

    public void onWriteCharacteristicComplete(int status, BluetoothDevice d,
            BleCharacteristic characteristic) {
        int nRetVal = 0;
    	
    	Log.d(TAG, "onWriteCharacteristicComplete");
        
    	
    }

    public void characteristicsRetrieved(BluetoothDevice d) {
        Log.d(TAG, "characteristicsRetrieved");
    }

    public void onRefreshComplete(BluetoothDevice d) {
        Log.d(TAG, "onRefreshComplete");
    }

    public void onSetCharacteristicAuthRequirement(BluetoothDevice d,
            BleCharacteristic characteristic, int instanceID) {
        Log.d(TAG, "onSetCharacteristicAuthRequirement");
    }

    public void onReadCharacteristicComplete(BluetoothDevice d, BleCharacteristic characteristic) {
        Log.d(TAG, "onReadCharacteristicComplete1");
        Intent intent = new Intent();
        intent.setAction(HELLOBLE_NAME);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, characteristic.getValue().toString());
        mContext.sendBroadcast(intent);
    }
    
    public void onReadCharacteristicComplete(int Status, BluetoothDevice d, BleCharacteristic characteristic) {
        Log.d(TAG, "onReadCharacteristicComplete2");
        Intent intent = new Intent();
        intent.setAction(HELLOBLE_NAME);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, characteristic.getValue().toString());
        mContext.sendBroadcast(intent);
    }

    public void onCharacteristicChanged(BluetoothDevice d, BleCharacteristic characteristic) {
        Log.d(TAG, "onCharacteristicChanged");
    }
}
