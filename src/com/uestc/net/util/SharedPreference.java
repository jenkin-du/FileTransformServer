package com.uestc.net.util;

import java.util.HashMap;

/**
 * 存储键值对的SharedPreference
 * 
 * @author jenkin
 *
 */
public class SharedPreference {

	private HashMap<String, String> spMap = new HashMap<>();

	public static String savePath = "G:\\tempFilePath.sp";

	public void put(String key, String value) {
		spMap.put(key, value);
	}

	public String get(String key) {
		return spMap.get(key);
	}

	public void remove(String key) {
		spMap.remove(key);
	}

	public void clear() {
		spMap.clear();
	}
	
	


	@Override
	public String toString() {
		return "SharedPreference [spMap=" + spMap + "]";
	}

	public HashMap<String, String> getSpMap() {
		return spMap;
	}

	public void setSpMap(HashMap<String, String> spMap) {
		this.spMap = spMap;
	}
	
	
}
