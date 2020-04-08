package com.swathub.jenkins.execution;

import hudson.EnvVars;
import hudson.FilePath;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
	public JSONObject apiGet(String apiUrl, String accessKey, String secretKey, final HashMap<String, String> proxy) throws Exception{
		JSONObject ret;

		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(accessKey, secretKey));
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.useSystemProperties();

		if (!proxy.get("server").isEmpty()) {
			credsProvider.setCredentials(
					new AuthScope(proxy.get("server"), Integer.parseInt(proxy.get("port"))),
					new UsernamePasswordCredentials(proxy.get("username"), proxy.get("password")));
			clientBuilder.setProxy(new HttpHost(proxy.get("server"), Integer.parseInt(proxy.get("port"))));
			clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
		}

		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		CloseableHttpClient httpclient = clientBuilder.build();
		HttpGet request = new HttpGet(apiUrl);

		HttpResponse response = httpclient.execute(request);

		if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception("SWAT Hub api call return false." + response.getStatusLine().getStatusCode());
		}

		BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
		String line;
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		httpclient.close();

		ret = new JSONObject().fromObject(result.toString());

		return ret;
	}

	public JSONObject apiPost(String apiUrl, String accessKey, String secretKey, String body, final HashMap<String, String> proxy) throws Exception{
		JSONObject ret;

		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(accessKey, secretKey));
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.useSystemProperties();

		if (!proxy.get("server").isEmpty()) {
			credsProvider.setCredentials(
					new AuthScope(proxy.get("server"), Integer.parseInt(proxy.get("port"))),
					new UsernamePasswordCredentials(proxy.get("username"), proxy.get("password")));
			clientBuilder.setProxy(new HttpHost(proxy.get("server"), Integer.parseInt(proxy.get("port"))));
			clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
		}

		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		CloseableHttpClient httpclient = clientBuilder.build();
		HttpPost request = new HttpPost(apiUrl);
		request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

		HttpResponse response = httpclient.execute(request);

		if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception("SWAT Hub api call return false." + response.getStatusLine().getStatusCode());
		}

		BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
		StringBuffer result = new StringBuffer();
		String line;
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		httpclient.close();

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
			out.writeAttribute("name", "Report");

			out.writeStartElement("testsuite");
			out.writeAttribute("tests", "1");
			out.writeAttribute("name", execResult.getString("name"));

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

	public String transform(String origin, EnvVars envVars) {
		if (origin == null) {
			origin = "";
		}
		Pattern p = Pattern.compile("\\$(\\w+)");
		Matcher m = p.matcher(origin);
		while (m.find()) {
			origin = origin.replace(m.group(), envVars.get(m.group(1), ""));
		}

		return origin;
	}
}
