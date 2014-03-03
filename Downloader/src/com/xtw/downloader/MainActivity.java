package com.xtw.downloader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	EditText mAddress, mPort;

	private int mPortValue;
	private String mAddressValue;
	private DownloadManager mDM;

	private BroadcastReceiver mReceiver;

	private void queryDownloadStatus(TextView display, long id) {
		DownloadManager.Query query = new DownloadManager.Query();
		query.setFilterById(id);
		Cursor c = mDM.query(query);
		if (c.moveToFirst()) {
			int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
			switch (status) {
			case DownloadManager.STATUS_PAUSED:
				display.setText("暂停");
			case DownloadManager.STATUS_PENDING:
				display.setText("正等待下载");
			case DownloadManager.STATUS_RUNNING:
				// 正在下载，不做任何事情
				display.setText("正在下载");
				break;
			case DownloadManager.STATUS_SUCCESSFUL:
				// 完成
				display.setText("下载完成");
				break;
			case DownloadManager.STATUS_FAILED:
// // 清除已下载的内容，重新下载
// Log.v("down", "STATUS_FAILED");
// mDM.remove(prefs.getLong(DL_ID, 0));
// prefs.edit().clear().commit();

				display.setText("下载失败");
				break;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mDM = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		int downloadingNum = preferences.getInt("downloadingNum", 0);
		final Button button = (Button) findViewById(R.id.main_start);
		if (downloadingNum != 0) {
			button.setEnabled(false);
			return;
		}
		String savedAddr = preferences.getString("addr", "");
		int savedPort = preferences.getInt("port", 8080);

		mAddress = (EditText) findViewById(R.id.main_ip);
		mPort = (EditText) findViewById(R.id.main_port);

		mAddress.setText(savedAddr);
		mPort.setText(String.valueOf(savedPort));

		button.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(final View v) {
				String addressValue = mAddress.getText().toString();
				String portValue = mPort.getText().toString();
				if (TextUtils.isEmpty(addressValue)) {
					mAddress.requestFocus();
				} else if (TextUtils.isEmpty(portValue)) {
					mPort.requestFocus();
				} else {
					mAddressValue = addressValue;
					mPortValue = Integer.parseInt(portValue);
					Editor edit = getPreferences(MODE_PRIVATE).edit();
					edit.putString("addr", addressValue).putInt("port", mPortValue).commit();

					AsyncTask<String, Integer, String> task = new AsyncTask<String, Integer, String>() {

						/*
						 * (non-Javadoc)
						 * 
						 * @see android.os.AsyncTask#onPreExecute()
						 */
						@Override
						protected void onPreExecute() {
							super.onPreExecute();
							v.setEnabled(false);
						}

						@Override
						protected String doInBackground(String... params) {
							String url = params[0];
							// 生成请求对象
							HttpGet httpGet = new HttpGet(url);
							HttpClient httpClient = new DefaultHttpClient();

							// 发送请求
							try {
								HttpResponse response = httpClient.execute(httpGet);
								// 显示响应
								// showResponseResult(response);//
// 一个私有方法，将响应结果显示出来

								HttpEntity httpEntity = response.getEntity();
								try {
									InputStream inputStream = httpEntity.getContent();
									BufferedReader reader = new BufferedReader(
											new InputStreamReader(inputStream));
									String result = "";
									String line = "";
									while (null != (line = reader.readLine())) {
										result += line;
									}
									return result;
								} catch (Exception e) {
									e.printStackTrace();
								}

							} catch (Exception e) {
								e.printStackTrace();
							}
							return null;
						}

						/*
						 * (non-Javadoc)
						 * 
						 * @see
						 * android.os.AsyncTask#onPostExecute(java.lang.Object)
						 */
						@Override
						protected void onPostExecute(String result) {

							v.setEnabled(true);
							super.onPostExecute(result);
							if (result == null) {
								return;
							}
							Log.e("TAG", (String) result);
							try {
								DocumentBuilder builder = DocumentBuilderFactory.newInstance()
										.newDocumentBuilder();
								Document doc = builder.parse(new ByteArrayInputStream(
										((String) result).getBytes()));
								List<String> filePaths = new ArrayList<String>();
								Element root = doc.getDocumentElement();
								getFilePath(root, filePaths);
								TextView tvConten = (TextView) findViewById(R.id.main_content);
								for (String url : filePaths) {
									tvConten.append(url + "\n");
									Uri resource = Uri.parse(String.format(
											"http://%s:%d/%s?download_and_delete=1", mAddressValue,
											mPortValue, url));
									DownloadManager.Request request = new DownloadManager.Request(
											resource);
									request.setAllowedNetworkTypes(Request.NETWORK_WIFI);
									request.setAllowedOverRoaming(false);
									// 设置文件类型
									MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
									String mimeString = mimeTypeMap
											.getMimeTypeFromExtension(MimeTypeMap
													.getFileExtensionFromUrl(url));
									request.setMimeType(mimeString);
									// 在通知栏中显示
									request.setShowRunningNotification(true);
									request.setVisibleInDownloadsUi(true);
									// sdcard的目录下的download文件夹
									File furl = new File(url);
									String path = furl.getParentFile().getName() + "/"
											+ furl.getName();
									File file = Environment
											.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
									file = new File(file, furl.getParent());
									file.mkdirs();
									request.setDestinationInExternalPublicDir(
											Environment.DIRECTORY_DOWNLOADS, path);
									mDM.enqueue(request);
								}
								if (!filePaths.isEmpty()) {
								} else {
									Toast.makeText(MainActivity.this, "没有要下载的文件",
											Toast.LENGTH_SHORT).show();
								}
							} catch (Exception e) {
								e.printStackTrace();
							}

						}

					};
// http://192.168.42.129:8080/?list_allfiles_xml=1
					// 使用GET方法发送请求,需要把参数加在URL后面，用？连接，参数之间用&分隔
					String url = String.format("http://%s:%d?list_allfiles_xml=1", mAddressValue,
							mPortValue);
					task.execute(url);
				}
			}
		});

		mReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				// 这里可以取得下载的id，这样就可以知道哪个文件下载完成了。适用与多个下载任务的监听
				if (intent.getAction().equals(DownloadManager.ACTION_VIEW_DOWNLOADS)
						|| intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
					startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
				} else if (intent.getAction().equals(DownloadManager.ACTION_NOTIFICATION_CLICKED)) {

				}
			}
		};
		IntentFilter filter = new IntentFilter(DownloadManager.ACTION_VIEW_DOWNLOADS);
		filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
		filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
		registerReceiver(mReceiver, filter);

	}

	private void getFilePath(Element root, List<String> filePaths) {
		Node f = root.getFirstChild();
		while (f != null) {
			if (f instanceof Element && f.getNodeName().equals("File")) {
				Element ef = (Element) f;
				String name = ef.getAttribute("Name");
				if (name.endsWith(".aac")) {
					Node n = ef;
					while (n != null) {
						n = n.getParentNode();
						if (n != null && n instanceof Element) {
							Element en = (Element) n;
							name = en.getAttribute("Name") + "/" + name;
						}
					}
					filePaths.add(name);
				} else {
					getFilePath(ef, filePaths);
				}
			}
			f = f.getNextSibling();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		super.onDestroy();
	}

}
