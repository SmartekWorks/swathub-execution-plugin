package com.swathub.jenkins.execution;

import hudson.FilePath;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.*;
import java.util.HashMap;

public class Utils {
	public JSONObject apiGet(String apiUrl, String accessKey, String secretKey, final HashMap<String, String> proxy) throws Exception{
		JSONObject ret;

		URL url = new URL(apiUrl);
		HttpURLConnection conn;
		if (!proxy.get("server").isEmpty()) {
			Proxy proxyServer = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.get("server"), Integer.parseInt(proxy.get("port"))));
			if (!proxy.get("username").isEmpty()) {
				Authenticator authenticator = new Authenticator() {
					public PasswordAuthentication getPasswordAuthentication() {
						return (new PasswordAuthentication(proxy.get("username"),
								proxy.get("password").toCharArray()));
					}
				};
				Authenticator.setDefault(authenticator);
			}
			conn = (HttpURLConnection) url.openConnection(proxyServer);
		} else {
			conn = (HttpURLConnection) url.openConnection();
		}
		conn.setDoOutput(true);
		conn.setRequestMethod("GET");

		String authString = accessKey + ":" + secretKey;
		byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
		String authStringEnc = new String(authEncBytes);
		conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception("SWAT Hub api call return false." + conn.getResponseCode());
		}

		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		StringBuilder result = new StringBuilder();
		String output;
		while ((output = br.readLine()) != null) {
			result.append(output);
		}

		conn.disconnect();

		ret = new JSONObject().fromObject(result.toString());

		return ret;
	}

	public JSONObject apiPost(String apiUrl, String accessKey, String secretKey, String body, final HashMap<String, String> proxy) throws Exception{
		JSONObject ret;

		URL url = new URL(apiUrl);
		HttpURLConnection conn;
		if (!proxy.get("server").isEmpty()) {
			Proxy proxyServer = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.get("server"), Integer.parseInt(proxy.get("port"))));
			if (!proxy.get("username").isEmpty()) {
				Authenticator authenticator = new Authenticator() {
					public PasswordAuthentication getPasswordAuthentication() {
						return (new PasswordAuthentication(proxy.get("username"),
								proxy.get("password").toCharArray()));
					}
				};
				Authenticator.setDefault(authenticator);
			}
			conn = (HttpURLConnection) url.openConnection(proxyServer);
		} else {
			conn = (HttpURLConnection) url.openConnection();
		}
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");

		String authString = accessKey + ":" + secretKey;
		byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
		String authStringEnc = new String(authEncBytes);
		conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

		OutputStream os = conn.getOutputStream();
		os.write(body.getBytes());
		os.flush();

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception("SWAT api call return false." + conn.getResponseCode());
		}

		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		StringBuilder result = new StringBuilder();
		String output;
		while ((output = br.readLine()) != null) {
			result.append(output);
		}

		conn.disconnect();

		ret = new JSONObject().fromObject(result.toString());

		return ret;
	}

	public void createXmlFile(FilePath fp, JSONObject execResult) {
		try {
			XMLStreamWriter out = XMLOutputFactory.newInstance().createXMLStreamWriter(
					new OutputStreamWriter(fp.write(), "utf-8"));

			out.writeStartDocument();
			out.writeStartElement("testsuites");
			out.writeAttribute("tests", "1");
			out.writeAttribute("name", execResult.getString("name"));

			out.writeStartElement("testsuite");
			out.writeAttribute("tests", "1");
			out.writeAttribute("name", "Test Set");

			JSONArray tasks = execResult.getJSONArray("tasks");
			for (int i = 0; i < tasks.size(); i++) {
				JSONObject task = tasks.getJSONObject(i);

				out.writeStartElement("testcase");
				out.writeAttribute("id", task.getString("taskID"));
				out.writeAttribute("name", task.getString("description"));
				out.writeAttribute("status", task.getString("status"));
				out.writeAttribute("time", String.valueOf(task.getDouble("duration")));
				if (task.has("error")) {
					out.writeStartElement("error");
					out.writeAttribute("message", task.getString("error"));
					out.writeEndElement();
				}
				out.writeEndElement();
			}

			out.writeEndElement();
			out.writeEndDocument();

			out.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
