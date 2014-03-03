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

import util.CommonMethod;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.TelephonyManager;

public class AutoAnswerReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {

		// Load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Check phone state
		String phone_state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
		String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
		G g = (G) context.getApplicationContext();
		MyMPUEntity entity = g.mEntity;
		String path = String.format("%s/%s", G.sRootPath, "mpudemo.txt");
		CommonMethod.save2File(phone_state, path, true);
		if (phone_state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
			String white_list = prefs.getString(G.KEY_WHITE_LIST, null);
			if (white_list == null) {
				CommonMethod.save2File("white_list is null!", path, true);
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
				CommonMethod.save2File(number + " not found in white_list!", path, true);
				return;
			}
			// Call a service, since this could take a few seconds
			CommonMethod.save2File("answer " + number, path, true);
			context.startService(new Intent(context, AutoAnswerIntentService.class));
		} else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(phone_state)) {
			if (entity != null) {
				CommonMethod.save2File("stop audio", path, true);
				entity.stopAudio();
			}
		} else if (phone_state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
			if (entity != null && !entity.isAudioStarted()) {
				CommonMethod.save2File("restart audio", path, true);
				entity.startOrRestart();
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
