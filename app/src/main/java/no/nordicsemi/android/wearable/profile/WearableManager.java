/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.wearable.profile;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

import no.nordicsemi.android.ble.callback.DataReceivedCallback;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.livedata.ObservableBleManager;
import no.nordicsemi.android.wearable.BuildConfig;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.wearable.utils.CurrentTimeService;

public class WearableManager extends ObservableBleManager {
	private LogSession logSession;
	private Uri currentUri;
	private boolean supported;

	public final static UUID NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
	private final static UUID RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
	private final static UUID TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
	private BluetoothGattCharacteristic txCharacteristic, rxCharacteristic;

	public WearableManager(@NonNull final Context context) {
		super(context);
		boolean success = CurrentTimeService.startServer(context);
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return new WearableBleManagerGattCallback();
	}

	/**
	 * Sets the log session to be used for low level logging.
	 * @param session the session, or null, if nRF Logger is not installed.
	 */
	public void setLogger(@Nullable final LogSession session) {
		logSession = session;
	}

	@Override
	public void log(final int priority, @NonNull final String message) {
		if (BuildConfig.DEBUG) {
			Log.println(priority, "WearableManager", message);
		}
		// The priority is a Log.X constant, while the Logger accepts it's log levels.
		Logger.log(logSession, LogContract.Log.Level.fromPriority(priority), message);
	}

	@Override
	protected boolean shouldClearCacheWhenDisconnected() {
		return !supported;
	}

	private final DataReceivedCallback txDataCallback = (device, data) -> {
		log(Log.INFO, "nusTx Received: " + data);
	};

	public void onCreateFile(Uri uri) {
		if (uri != null) {
			currentUri = uri;
			log(Log.INFO, "onCreateFile: " + currentUri.getPath());
		}
	}

	/**
	 * BluetoothGatt callbacks object.
	 */
	private class WearableBleManagerGattCallback extends BleManagerGattCallback {
		@Override
		protected void initialize() {
			setNotificationCallback(txCharacteristic).with(txDataCallback);
			enableNotifications(txCharacteristic).enqueue();
		}

		@Override
		public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {

			final BluetoothGattService service = gatt.getService(NUS_SERVICE_UUID);
			if (service != null) {
				rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID);
				txCharacteristic = service.getCharacteristic(TX_CHAR_UUID);
			}

			boolean writeRequest = false;
			if (rxCharacteristic != null) {
				final int ledProperties = rxCharacteristic.getProperties();
				writeRequest = (ledProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}

			boolean notifyRequest = false;
			if (txCharacteristic != null) {
				final int ledProperties = txCharacteristic.getProperties();
				notifyRequest = (ledProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
			}

			supported = txCharacteristic != null && rxCharacteristic != null &&
						writeRequest && notifyRequest;
			return supported;
		}

		@Override
		protected void onServicesInvalidated() {
			txCharacteristic = null;
			rxCharacteristic = null;
		}
	}
}
