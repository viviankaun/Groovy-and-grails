package org.fcm.send.api;

import grails.converters.JSON;
import co.wetogether.wco.open.PostFCMController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.HttpStatus;
import org.codehaus.groovy.grails.web.json.JSONObject;

import com.google.gson.Gson;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FCM {

	public static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";
	//public static final String FCM_SERVER_API_KEY = "AAAAb4Ob0w0:APA91bFnzno9CqS5qFeDT55fmPttq_kd1xyLjMFDcrBs4lbofQihz7eRo9nqV4zPjKwXNmSnfuhLI9AjJ2D2iCVBU-dfE2myvjiQ_dixSW1DniMdiG7Ib0T3EeKum0DTrl_raWMMSb9U";
	public static final String FCM_SERVER_API_KEY = "_________________________________________________V";
	
	public static Map<String, Object> responseMap = new LinkedHashMap();
	private static Map<Object, Object> payloadMap = new LinkedHashMap();
	private static ArrayList<Object> fcmDataResults = new ArrayList<Object>();
	
	
	public static void postFCM() throws JsonParseException, JsonMappingException, IOException {
		
			
		for (Object o : fcmDataResults) {
			
			Map<String, String> reBuildNotificationMap = new HashMap();
			reBuildNotificationMap.put("title", convertJsonMap(o).get("title").toString());
			reBuildNotificationMap.put("body", convertJsonMap(o).get("content").toString());
			
			payloadMap.put("notificationID", convertJsonMap(o).get("id").toString());
			payloadMap.put("to", convertJsonMap(o).get("rid").toString());
			payloadMap.put("notification", reBuildNotificationMap);
			
			sendHttpConnection();
			
			
		}
	}
	

	public static void setFCMData(ArrayList<Object> sqlResults) {		
		fcmDataResults = sqlResults;
	}

	public static void sendHttpConnection() {
		int responseCode = -1;
		String responseBody = null;
		
		try {
			System.out.println("Sending FCM request");
			String postData = new JSONObject(payloadMap).toString();
			
			URL url = new URL(FCM_URL);
			HttpsURLConnection httpURLConnection = (HttpsURLConnection) url.openConnection();

			httpURLConnection.setConnectTimeout(1000);
			httpURLConnection.setReadTimeout(1000);
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setUseCaches(false);
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Body", postData);
			httpURLConnection.setRequestProperty("Authorization", "key=" + FCM_SERVER_API_KEY);

			OutputStreamWriter out = new OutputStreamWriter(httpURLConnection.getOutputStream());
			out.write(postData);
			out.close();
			
			PostFCMController postFCMController = new PostFCMController();
			responseCode = httpURLConnection.getResponseCode();
			// success
			if (responseCode == HttpStatus.SC_OK) {
				responseBody = convertStreamToString(httpURLConnection.getInputStream());
				
				System.out.println("FCM message sent : " + responseBody);
				
				responseMap.put("notificationID", payloadMap.get("notificationID").toString());
				responseMap.put("responseResult", JSON.parse(responseBody));
			}
			// failure
			else {
				responseBody = convertStreamToString(httpURLConnection.getErrorStream());
				
				System.out.println("Sending FCM request failed for regId: " + payloadMap.get("to") + " response: " + responseBody);

				responseMap.put("notificationID", payloadMap.get("notificationID").toString());
				responseMap.put("responseResult", JSON.parse(responseBody));
			}

		} 
		catch (IOException ioe) {
			System.out.println("IO Exception in sending FCM request. regId: " + payloadMap.get("to"));
			ioe.printStackTrace();
		}
		catch (Exception e) {
			System.out.println("Unknown exception in sending FCM request. regId: " + payloadMap.get("to"));
			e.printStackTrace();
		}
	}


	public static String convertStreamToString(InputStream inStream) throws Exception {
		InputStreamReader inputStream = new InputStreamReader(inStream);
		BufferedReader bReader = new BufferedReader(inputStream);

		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = bReader.readLine()) != null) {
			sb.append(line);
		}

		return sb.toString();
	}
	
	private static Map<Object, Object> convertJsonMap(Object mapObject) throws JsonParseException, JsonMappingException, IOException {
		Gson gson = new Gson();
		String json = gson.toJson(mapObject);
		Map<Object, Object> fcmDataMap =  new HashMap<Object, Object>();
		fcmDataMap = (Map<Object, Object>)JSON.parse(json);
		//new ObjectMapper().readValue(json, HashMap.class);
		return fcmDataMap;
		
	}
}
