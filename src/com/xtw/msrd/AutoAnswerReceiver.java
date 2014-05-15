/*
 * AutoAnswer
 * Copyright (C) 2010 EverySoft
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.xtw.msrd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.TelephonyManager;
import android.util.Log;

public class AutoAnswerReceiver extends BroadcastReceiver {

	private static final String tag = "AutoAnswer";

	@Override
	public void onReceive(Context context, Intent intent) {

		// Load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Check phone state
		String phone_state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
		String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
		MyMPUEntity entity = G.sEntity;
		
		G.log(phone_state);
		if (phone_state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
			String white_list = prefs.getString(G.KEY_WHITE_LIST, null);
			if (white_list == null) {
				G.log("white_list is null!");
				return;
			}
			boolean found = false;
			String[] numbers = white_list.split(",");
			for (int i = 0; i < numbers.length; i++) {
				if (numbers[i].equals(number)) {
					found = true;
					break;
				}
			}
			// Check for "second call" restriction
			if (!found) {
				G.log(number + " not found in white_list!");
				return;
			}
			// Call a service, since this could take a few seconds
			G.log("answer " + number);
			context.startService(new Intent(context, AutoAnswerIntentService.class));
		} else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(phone_state)) {
			if (entity != null) {
				G.log("stop audio");
				Log.e(tag, "ring,close");
				prefs.edit().putBoolean("prev_record", entity.isLocalRecord()).commit();
				entity.stopAudio();
			}
		} else if (phone_state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
			if (entity != null && !entity.isAudioStarted()) {
				G.log("restart audio");
				entity.setLocalRecord(prefs.getBoolean("prev_record", true));
				Log.e(tag, "ring stoped,reopen");
			}
		}
	}

	// returns -1 if not in contact list, 0 if not starred, 1 if starred
	private int isStarred(Context context, String number) {
		int starred = -1;
		Cursor c = context.getContentResolver().query(
				Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, number),
				new String[] { PhoneLookup.STARRED }, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				starred = c.getInt(0);
			}
			c.close();
		}
		return starred;
	}
}
