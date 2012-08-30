package org.osmdroid.bonuspack.location;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.bonuspack.utils.BonusPackHelper;
import org.osmdroid.bonuspack.utils.HttpConnection;
import org.osmdroid.util.GeoPoint;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import android.util.Log;

/**
 * POI Provider using GeoNames services. 
 * Currently, "find Nearby Wikipedia" service. 
 * @see http://www.geonames.org
 * @author M.Kergall
 *
 */
public class GeoNamesPOIProvider {

	protected String mUserName;
	
	/**
	 * @param account the registered "username" to give to GeoNames service. 
	 * @see http://www.geonames.org/login
	 */
	public GeoNamesPOIProvider(String account){
		mUserName = account;
	}
	
	private String getUrlCloseTo(GeoPoint p, int maxResults, double maxDistance){
		StringBuffer url = new StringBuffer("http://api.geonames.org/findNearbyWikipediaJSON?");
		url.append("lat="+p.getLatitudeE6()*1E-6);
		url.append("&lng="+p.getLongitudeE6()*1E-6);
		url.append("&maxRows="+maxResults);
		url.append("&radius="+maxDistance); //km
		url.append("&lang="+Locale.getDefault().getLanguage());
		url.append("&username="+mUserName);
		return url.toString();
	}
	
	/**
	 * @param fullUrl
	 * @return the list of POI
	 */
	public ArrayList<POI> getThem(String fullUrl){
		Log.d(BonusPackHelper.LOG_TAG, "GeoNamesPOIProvider:get:"+fullUrl);
		String jString = BonusPackHelper.requestStringFromUrl(fullUrl);
		if (jString == null) {
			Log.e(BonusPackHelper.LOG_TAG, "GeoNamesPOIProvider: request failed.");
			return null;
		}
		try {
			JSONObject jRoot = new JSONObject(jString);
			JSONArray jPlaceIds = jRoot.getJSONArray("geonames");
			int n = jPlaceIds.length();
			ArrayList<POI> pois = new ArrayList<POI>(n);
			for (int i=0; i<n; i++){
				JSONObject jPlace = jPlaceIds.getJSONObject(i);
				POI poi = new POI();
				poi.mLocation = new GeoPoint(jPlace.getDouble("lat"), 
						jPlace.getDouble("lng"));
				poi.mCategory = jPlace.optString("feature");
				poi.mType = jPlace.getString("title");
				poi.mDescription = jPlace.optString("summary");
				poi.mThumbnailPath = jPlace.optString("thumbnailImg", null);
				/* This makes loading too long. 
				 * Thumbnail loading will be done only when needed, with POI.getThumbnail()
				if (poi.mThumbnailPath != null){
					poi.mThumbnail = BonusPackHelper.loadBitmap(poi.mThumbnailPath);
				}
				*/
				poi.mUrl = jPlace.optString("wikipediaUrl", null);
				if (poi.mUrl != null)
					poi.mUrl = "http://" + poi.mUrl;
				//other attributes: distance, rank?
				pois.add(poi);
			}
			Log.d(BonusPackHelper.LOG_TAG, "done");
			return pois;
		}catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	//XML parsing seems 2 times slower than JSON parsing
	public ArrayList<POI> getThemXML(String fullUrl){
		Log.d(BonusPackHelper.LOG_TAG, "GeoNamesPOIProvider:get:"+fullUrl);
		HttpConnection connection = new HttpConnection();
		connection.doGet(fullUrl);
		InputStream stream = connection.getStream();
		if (stream == null){
			return null;
		}
		XMLHandler handler = new XMLHandler();
		try {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			parser.parse(stream, handler);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		connection.close();
		Log.d(BonusPackHelper.LOG_TAG, "done");
		return handler.mPOIs;
	}
	
	/**
	 * @param position
	 * @param maxResults
	 * @param maxDistance in km. 20 km max for the free service. 
	 * @return list of POI, Wikipedia entries close to the position. Null if technical issue. 
	 */
	public ArrayList<POI> getPOICloseTo(GeoPoint position, 
			int maxResults, double maxDistance){
		String url = getUrlCloseTo(position, maxResults, maxDistance);
		return getThem(url);
	}
}

class XMLHandler extends DefaultHandler {
	
	private String mString;
	double mLat, mLng;
	POI mPOI;
	ArrayList<POI> mPOIs;
	
	public XMLHandler() {
		mPOIs = new ArrayList<POI>();
	}
	
	@Override public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		if (localName.equals("entry")){
			mPOI = new POI();
		}
		mString = new String();
	}
	
	@Override public void characters(char[] ch, int start, int length)
	throws SAXException {
		String chars = new String(ch, start, length);
		mString = mString.concat(chars);
	}

	@Override public void endElement(String uri, String localName, String name)
	throws SAXException {
		if (localName.equals("lat")) {
			mLat = Double.parseDouble(mString);
		} else if (localName.equals("lng")) {
			mLng = Double.parseDouble(mString);
		} else if (localName.equals("feature")){
			mPOI.mCategory = mString;
		} else if (localName.equals("title")){
			mPOI.mType = mString;
		} else if (localName.equals("summary")){
			mPOI.mDescription = mString;
		} else if (localName.equals("thumbnailImg")){
			if (mString != null && !mString.equals(""))
				mPOI.mThumbnailPath = mString;
		} else if (localName.equals("wikipediaUrl")){
			if (mString != null && !mString.equals(""))
				mPOI.mUrl = "http://" + mString;
		} else if (localName.equals("rank")){
			//TODO ...
		} else if (localName.equals("entry")) {
			mPOI.mLocation = new GeoPoint(mLat, mLng);
			mPOIs.add(mPOI);
		};
	}

}
