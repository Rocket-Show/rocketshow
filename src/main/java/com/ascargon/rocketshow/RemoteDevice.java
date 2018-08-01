package com.ascargon.rocketshow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

/**
 * Defines a remote RocketShow device to be triggered by the local one one.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class RemoteDevice {

	final static Logger logger = Logger.getLogger(RemoteDevice.class);

	private HttpClient httpClient;

	// The name of the remote device
	private String name;

	// The host address (IP or hostname) of the remote device
	private String host;

	// Synchronize composition plays/stops with the local device
	private boolean synchronize;

	public RemoteDevice() {
		// TODO Add this timeout to the settings
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
	}

	private void executeRequest(String url) {
		try {
			HttpPost httpPost = new HttpPost(url);
			HttpResponse response;

			response = httpClient.execute(httpPost);

			// Read the response. The POST connection will not be
			// released
			// otherwise
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

			String line = "";

			while ((line = rd.readLine()) != null) {
				logger.debug("Response from remote device POST: " + line);
			}

			if (response.getStatusLine().getStatusCode() != 200) {
				logger.error("Could not execute action on remote device with url '" + url + "'. Reason: '"
						+ response.getStatusLine().getReasonPhrase() + "'. Body: "
						+ EntityUtils.toString(response.getEntity()));
			}
		} catch (Exception e) {
			logger.error("Could not execute action on remote device '" + name + "' with url '" + url + "'", e);
		}
	}

	public void doPost(String apiUrl, boolean synchronous) {
		// Build the url for the post request
		String url = "http://" + host + "/api/" + apiUrl;

		if (synchronous) {
			executeRequest(url);
		} else {
			new Thread(new Runnable() {
				public void run() {
					executeRequest(url);
				}
			}).start();
		}
	}

	public void doPost(String apiUrl) {
		doPost(apiUrl, false);
	}

	public void reboot() {
		doPost("system/reboot");
	}

	public void load(boolean synchronous, String name, long positionMillis) {
		try {
			doPost("transport/load?name=" + URLEncoder.encode(name, "UTF-8") + "&positionMillis=" + positionMillis,
					synchronous);
		} catch (UnsupportedEncodingException e) {
			logger.error("Could not encode the name " + name, e);
		}
	}

	public void load() {
		doPost("transport/load", false);
	}

	public void play() {
		doPost("transport/play");
	}

	public void pause() {
		doPost("transport/pause");
	}

	public void stop(boolean playDefaultComposition, boolean resetPosition) {
		doPost("transport/stop?playDefaultComposition=" + playDefaultComposition + "&resetPosition=" + resetPosition);
	}

	public void togglePlay() {
		doPost("transport/toggle-play");
	}

	public void setNextComposition() {
		doPost("transport/next-composition");
	}

	public void setPreviousComposition() {
		doPost("transport/previous-composition");
	}

	public void setCompositionName(String compositionName) {
		doPost("transport/set-composition-name?name=" + compositionName, true);
	}

	public void setCompositionIndex(int compositionIndex) {
		doPost("transport/set-composition-index?index=" + compositionIndex, true);
	}

	@XmlElement
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@XmlElement
	public boolean isSynchronize() {
		return synchronize;
	}

	public void setSynchronize(boolean synchronize) {
		this.synchronize = synchronize;
	}

}
