package com.ampu;

import java.io.IOException;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.Assert;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.util.SparseArray;

public class DisplayFilter {
	private static final String tag = "DisplayFilter";
	private static final String ErrorCode = "ErrorCode";
	private static final String NUMBER = "Number";
	private static final String INTEGER = "Integer";
	private static SparseArray<String> sMap = null;
	private static int sRadix = 16;
	/**
	 * 当前语言
	 */
	private static String sLanguage;

	/**
	 * 翻译错误码
	 * 
	 * @param context
	 * @param errorCode
	 * @return
	 */
	public static String translate(Context context, int errorCode) {
		// 判断语言项
		if (sMap != null) {
			if (!sLanguage.equals(Locale.getDefault().getLanguage())) {
				sLanguage = Locale.getDefault().getLanguage();
				sMap.clear();
				sMap = null;
			}
		}
		if (sMap == null) {
			sMap = new SparseArray<String>();
			sLanguage = Locale.getDefault().getLanguage();
			String langTag = null;
			// 其他语言都使用英语
			if (sLanguage.equals("zh")) {
				langTag = "LID_0804";
			} else {
				langTag = "LID_0409";
			}

			final String LANGUAGE_LOCAL_NAME = langTag;
			SAXParserFactory factory = SAXParserFactory.newInstance();
			try {
				SAXParser parser = factory.newSAXParser();
				XMLReader reader = parser.getXMLReader();
				reader.setContentHandler(new DefaultHandler() {
					private String mLastErrorCode = null;
					private String mLastLanguageTag = null;

					@Override
					public void startElement(String uri, String localName, String qName,
							Attributes attributes) throws SAXException {
						if (localName.equals(ErrorCode)) {
							mLastErrorCode = attributes.getValue(NUMBER);
							String attr_value;
							if (mLastErrorCode == null) {
								attr_value = attributes.getValue(INTEGER);
								sRadix = Integer.parseInt(attr_value);
							}
						} else if (localName.equals(LANGUAGE_LOCAL_NAME)) {
							// sMap.put(mLastErrorCode, desc);
							mLastLanguageTag = localName;
						}
					}

					@Override
					public void endElement(String uri, String localName, String qName)
							throws SAXException {
						super.endElement(uri, localName, qName);
						if (localName.equals(ErrorCode)) {
							mLastErrorCode = null;
						} else if (localName.equals(LANGUAGE_LOCAL_NAME)) {
							// sMap.put(mLastErrorCode, desc);
							mLastLanguageTag = null;
						}
					}

					@Override
					public void characters(char[] ch, int start, int length) throws SAXException {
						super.characters(ch, start, length);
						if (mLastLanguageTag != null) {
							Assert.assertNotNull(mLastErrorCode);
							int lastErrorCode = Integer.parseInt(mLastErrorCode.substring(2),
									sRadix);
							String value = new String(ch, start, length);
							sMap.put(lastErrorCode, value);
						}
					}

				});
				reader.parse(new InputSource(context.getResources().openRawResource(
						R.raw.display_filter)));
			} catch (NotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			}
		}
		String desc = sMap.get(errorCode);
		String oxError = String.format("0X%04X", errorCode);
		return desc == null ? context.getString(R.string._unkown_error, oxError) : String.format(
				sLanguage.equals("zh") ? "%s （%s）" : "%s (%s)", desc, oxError);
	}
}
