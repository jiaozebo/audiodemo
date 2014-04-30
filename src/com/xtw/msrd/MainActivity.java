package com.xtw.msrd;

import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.crearo.config.Apn;
import com.crearo.config.Apn.ApnNode;
import com.crearo.config.Connectivity;
import com.crearo.puserver.PUServerThread;
import com.xtw.msrd.G.LoginStatus;

public class MainActivity extends PreferenceActivity implements OnClickListener {

	private Button mLoginBtn;
	private Runnable mCallback;
	private AlertDialog mApnDlg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (TextUtils.isEmpty(G.sRootPath)) {
			G.initRoot();
		}
		if (TextUtils.isEmpty(G.sRootPath)) {
			Toast.makeText(this, "请检查SD卡配置！", Toast.LENGTH_SHORT).show();
			return;
		}
		final G g = (G) getApplication();
		if (G.mEntity == null) {
			g.gloableInit();
		}
		startService(new Intent(this, WifiAndPuServerService.class));
		setContentView(R.layout.activity_main);
		View footerView = getLayoutInflater().inflate(R.layout.list_footer, getListView(), false);
		getListView().addFooterView(footerView);
		mLoginBtn = (Button) findViewById(R.id.btn_start);
		// mLoginBtn.setOnClickListener(this);

		mCallback = new Runnable() {

			@Override
			public void run() {
				LoginStatus ls = G.getLoginStatus();
				if (ls == LoginStatus.STT_PRELOGIN) {
					mLoginBtn.setText("未登录");
				} else if (ls == LoginStatus.STT_LOGINING) {
					mLoginBtn.setText("正在登录");
				} else {
					mLoginBtn.setText("已登录");
				}
			}
		};
		g.registerLoginStatusChangedCallback(mCallback);
		mCallback.run();
		LoginStatus ls = G.getLoginStatus();
		if (ls == LoginStatus.STT_PRELOGIN) {
			if (g.checkParam(true)) {
				startService(new Intent(this, MsrdService.class));
			} else {
				getListView().setVisibility(View.VISIBLE);
			}
		}

		TextView tView = (TextView) findViewById(R.id.ip_address);
		List<String> ips = PUServerThread.getAllIpAddress();

		tView.setText("本机IP： ");
		for (Iterator iterator = ips.iterator(); iterator.hasNext();) {
			String ip = (String) iterator.next();
			tView.append("\n" + ip);
		}

		findViewById(R.id.btn_add_apn).setOnClickListener(this);
		tryEnableMobileData();

		try {
			TextView view = (TextView) findViewById(R.id.version);
			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			view.setText("版本：" + pi.versionName);
		} catch (Exception e) {
		}

	}

	private void tryEnableMobileData() {
		if (!Connectivity
				.isMobileDataEnabled((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))) {
			Connectivity.setMobileDataEnable(
					(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE), true);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		getListView().setCacheColorHint(0);
		setupSimplePreferencesScreen();
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI should be
	 * shown.
	 */
	private void setupSimplePreferencesScreen() {

		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.

		// Add 'general' preferences.
		addPreferencesFromResource(R.xml.pref_pu_platform_address);
		PreferenceCategory fakeHeader = new PreferenceCategory(this);

		bindPreferenceSummaryToValue(findPreference(G.KEY_SERVER_ADDRESS));
		bindPreferenceSummaryToValue(findPreference(G.KEY_SERVER_PORT));
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btn_start) {
			final Button button = (Button) v;
			if (button.getText().equals("未登录")) {
				startService(new Intent(this, MsrdService.class));
			} else {
			}
		} else if (R.id.btn_add_apn == v.getId()) {
			final View view = getLayoutInflater().inflate(R.layout.dlg_apn, null);
			view.findViewById(R.id.apn_add).setOnClickListener(this);
			view.findViewById(R.id.apn_cancel).setOnClickListener(this);
			mApnDlg = new AlertDialog.Builder(this).setView(view).show();
		} else if (R.id.apn_add == v.getId()) {
			EditText et = (EditText) mApnDlg.findViewById(R.id.apn_name);
			String name = et.getText().toString();
			et.setError(null);
			if (TextUtils.isEmpty(name)) {
				et.requestFocus();
				et.setError("不能为空");
				return;
			}
			et = (EditText) mApnDlg.findViewById(R.id.apn_apn);
			String apn = et.getText().toString();
			et.setError(null);
			if (TextUtils.isEmpty(apn)) {
				et.requestFocus();
				et.setError("不能为空");
				return;
			}
			et = (EditText) mApnDlg.findViewById(R.id.apn_usr);
			String usr = et.getText().toString();
			et = (EditText) mApnDlg.findViewById(R.id.apn_port);
			String port = et.getText().toString();
			et = (EditText) mApnDlg.findViewById(R.id.apn_server);
			String server = et.getText().toString();
			et = (EditText) mApnDlg.findViewById(R.id.apn_pwd);
			String pwd = et.getText().toString();
			ApnNode node = Apn.getDefaultApn(this);
			if (node == null) {
				node = new ApnNode();
			}
			node.name = name;
			node.apn = apn;
			node.user = usr;
			node.password = pwd;
			node.server = server;
			node.port = port;
			int id = node.id;
			if (id == -1) {
				Uri uri = Apn.addApn(this, node);
				String idStr = uri.getLastPathSegment();
				if (idStr != null) {
					id = Integer.parseInt(idStr);
				}
			} else {
				Apn.updateApn(this, id, node);
			}
			id = Apn.setDefault(this, id);
			if (id == 1) {
				Toast.makeText(this, "设置成功", Toast.LENGTH_SHORT).show();
				mApnDlg.cancel();
				mApnDlg = null;
			}
		} else if (R.id.apn_cancel == v.getId()) {
			mApnDlg.cancel();
			mApnDlg = null;
		}
	}

	@Override
	protected void onDestroy() {
		if (mCallback != null) {
			G g = (G) getApplication();
			g.unRegisterLoginStatusChangedCallback(mCallback);
		}
		super.onDestroy();
	}

	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 * 
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(
						preference.getKey(), ""));
	}
}
