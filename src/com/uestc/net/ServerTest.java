package com.uestc.net;

import com.uestc.net.api.FileServer;

public class ServerTest {

	public static void main(String[] args) {

		FileServer server = new FileServer();
		try {
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
