/*
 * Copyright (C) 2026 VoltageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.quickspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import com.android.launcher3.LauncherPrefs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuickBatteryController {

  private static final String ACTION_BLUETOOTH_BATTERY_UPDATE =
      "com.android.systemui.action.BLUETOOTH_BATTERY_UPDATE";

  private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;

  private final Context mContext;
  private final QuickspaceController mController;
  private boolean mRegistered = false;

  public static class BatteryDevice {
    public String name;
    public int level;
    public boolean isAudio;
    public String address;

    public BatteryDevice(String name, int level, boolean isAudio, String address) {
      this.name = name;
      this.level = level;
      this.isAudio = isAudio;
      this.address = address;
    }
  }

  private final List<BatteryDevice> mDevices = new ArrayList<>();

  private String mCurrentDeviceAddress = null;
  private int mCurrentIndex = 0;
  private long mLastInteractionTime = 0;
  private final Set<String> mAlertedDevices = new HashSet<>();

  private final BroadcastReceiver mReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (ACTION_BLUETOOTH_BATTERY_UPDATE.equals(intent.getAction())) {
            mDevices.clear();

            ArrayList<String> names = intent.getStringArrayListExtra("device_list_names");
            ArrayList<Integer> levels = intent.getIntegerArrayListExtra("device_list_levels");
            ArrayList<String> audioFlags = intent.getStringArrayListExtra("device_list_audio");
            ArrayList<String> addresses = intent.getStringArrayListExtra("device_list_addresses");

            if (names != null && levels != null && !names.isEmpty()) {
              int count = Math.min(names.size(), levels.size());
              if (audioFlags != null) count = Math.min(count, audioFlags.size());

              for (int i = 0; i < count; i++) {
                boolean isAudio = false;
                if (audioFlags != null) {
                  isAudio = Boolean.parseBoolean(audioFlags.get(i));
                }

                String addr =
                    (addresses != null && i < addresses.size()) ? addresses.get(i) : names.get(i);

                int level = Math.max(0, Math.min(100, levels.get(i)));
                mDevices.add(new BatteryDevice(names.get(i), level, isAudio, addr));
              }

              if (SystemClock.elapsedRealtime() - mLastInteractionTime > SESSION_TIMEOUT_MS) {
                mCurrentDeviceAddress = null;
              }

              int newIndex = 0;
              if (mCurrentDeviceAddress != null) {
                for (int i = 0; i < mDevices.size(); i++) {
                  if (mDevices.get(i).address.equals(mCurrentDeviceAddress)) {
                    newIndex = i;
                    break;
                  }
                }
              }
              mCurrentIndex = newIndex;

              if (mDevices.size() > 1) {
                for (int i = 0; i < mDevices.size(); i++) {
                  BatteryDevice d = mDevices.get(i);

                  if (d.level > 20) {
                    mAlertedDevices.remove(d.address);
                  }

                  if (d.level <= 15 && !mAlertedDevices.contains(d.address)) {
                    mCurrentIndex = i;
                    mCurrentDeviceAddress = d.address;
                    mAlertedDevices.add(d.address);
                    mLastInteractionTime = SystemClock.elapsedRealtime();
                    break;
                  }
                }
              }

              if (!mDevices.isEmpty()) {
                mCurrentDeviceAddress = mDevices.get(mCurrentIndex).address;
              }

            } else {
              mCurrentIndex = 0;
              mCurrentDeviceAddress = null;
            }
            mController.notifyListeners();
          }
        }
      };

  public QuickBatteryController(Context context, QuickspaceController controller) {
    mContext = context;
    mController = controller;
    onResume();
  }

  public void onResume() {
    if (LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(mContext)) {
      Intent stickyIntent = registerReceiver();
      if (stickyIntent != null) {
        mReceiver.onReceive(mContext, stickyIntent);
      }
    } else {
      clearData();
    }
  }

  public void onPause() {
    unRegisterReceiver();
  }

  private Intent registerReceiver() {
    if (mRegistered) return null;
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_BLUETOOTH_BATTERY_UPDATE);
    Intent sticky = null;
    try {
      sticky = mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
      mRegistered = true;
    } catch (Exception e) {
      mRegistered = false;
    }
    return sticky;
  }

  private void unRegisterReceiver() {
    if (!mRegistered) return;
    try {
      mContext.unregisterReceiver(mReceiver);
    } catch (Exception e) {
      // ignore
    }
    mRegistered = false;
  }

  private void clearData() {
    if (mDevices.isEmpty()) return;
    mDevices.clear();
    mCurrentDeviceAddress = null;
    mController.notifyListeners();
  }

  /** Cycles to the next device in the list. Called when the user taps the battery pill. */
  public void advanceIndex() {
    if (mDevices.isEmpty()) return;
    mCurrentIndex = (mCurrentIndex + 1) % mDevices.size();
    mCurrentDeviceAddress = mDevices.get(mCurrentIndex).address;
    mLastInteractionTime = SystemClock.elapsedRealtime();
  }

  public BatteryDevice getCurrentDevice() {
    if (mDevices.isEmpty()) return null;
    if (mCurrentIndex >= mDevices.size()) mCurrentIndex = 0;
    return mDevices.get(mCurrentIndex);
  }

  public int getDeviceCount() {
    return mDevices.size();
  }

  public int getCurrentIndex() {
    return mCurrentIndex;
  }

  public String getDeviceName() {
    BatteryDevice d = getCurrentDevice();
    return d != null ? d.name : null;
  }

  public int getBatteryLevel() {
    BatteryDevice d = getCurrentDevice();
    return d != null ? d.level : -1;
  }

  public boolean isAudioDevice() {
    BatteryDevice d = getCurrentDevice();
    return d != null ? d.isAudio : false;
  }

  public void launchBatterySettings() {
    try {
      Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      mContext.startActivity(intent);
    } catch (Exception e) {
    }
  }
}
