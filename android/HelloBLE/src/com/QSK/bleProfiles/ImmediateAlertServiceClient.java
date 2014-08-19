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
import android.util.Log;

import com.broadcom.bt.le.api.BleCharacteristic;
import com.broadcom.bt.le.api.BleClientService;
import com.broadcom.bt.le.api.BleGattID;

public class ImmediateAlertServiceClient extends BleClientService {
    public static String TAG = "ImmediateAlertServiceClient";
    
    static public BleGattID myUuid = new BleGattID("00001802-0000-1000-8000-00805f9b34fb");
    
    public ImmediateAlertServiceClient() {
        super(myUuid);
        Log.d(TAG, "ImmediateAlertServiceClient");
    }

    public void onWriteCharacteristicComplete(int status, BluetoothDevice d,
            BleCharacteristic characteristic) {
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
        Log.d(TAG, "refreshOneCharacteristicComplete");
    }

    public void onCharacteristicChanged(BluetoothDevice d, BleCharacteristic characteristic) {
        Log.d(TAG, "onCharacteristicChanged");
    }
}
