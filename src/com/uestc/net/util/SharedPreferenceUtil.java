package com.uestc.net.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;

import com.alibaba.fastjson.JSON;

/**
 * SharedPrefrence帮助类，用于保存键值对
 * 
 * @author jenkin
 *
 */
public class SharedPreferenceUtil {

	/**
	 * 保存
	 * 
	 * @param key
	 * @param value
	 */
	public static void save(String key, String value) {

		try {
			SharedPreference sp;

			File file = new File(SharedPreference.savePath);
			if (!file.exists()) {
				file.createNewFile();
			}

			long length = file.length();
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			if (length == 0) {
				sp = new SharedPreference();
				sp.put(key, value);

				// 序列化为json
				String json = JSON.toJSONString(sp);
				// 写入文件，
				raf.write(json.getBytes());
				raf.close();
			} else {
				// 读出已有的内容
				byte[] bytes = new byte[(int) length];
				raf.read(bytes);
				raf.close();

				String json = new String(bytes);
				sp = JSON.parseObject(json, SharedPreference.class);
				if (sp == null) {
					sp = new SharedPreference();
				}
				// 添加
				sp.put(key, value);
				String jsonStr = JSON.toJSONString(sp);
				// 写文件
				FileOutputStream fos = new FileOutputStream(file);
				fos.write(jsonStr.getBytes());
				fos.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * 获取，如果找不到则返回空
	 * 
	 * @param key
	 * 
	 */
	public static String get(String key) {

		try {
			SharedPreference sp;

			File file = new File(SharedPreference.savePath);

			if (!file.exists() || file.length() == 0) {
				return null;
			}
			long length = file.length();
			RandomAccessFile raf = new RandomAccessFile(file, "r");

			// 读出已有的内容
			byte[] bytes = new byte[(int) length];
			raf.read(bytes);
			raf.close();

			String json = new String(bytes);
			sp = JSON.parseObject(json, SharedPreference.class);
			if (sp == null) {
				return null;
			}
			// 获取
			return sp.get(key);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * 删除对应key的value
	 * 
	 * @param key
	 */
	public static void remove(String key) {

		try {
			SharedPreference sp;

			File file = new File(SharedPreference.savePath);
			if (!file.exists() || file.length() == 0) {
				return;
			}

			long length = file.length();
			RandomAccessFile raf = new RandomAccessFile(file, "rw");

			// 读出已有的内容
			byte[] bytes = new byte[(int) length];
			raf.read(bytes);
			raf.close();

			String json = new String(bytes);
			sp = JSON.parseObject(json, SharedPreference.class);
			if (sp == null) {
				return;
			}
			// 删除
			sp.remove(key);

			// 写文件
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(JSON.toJSONString(sp).getBytes());
			fos.close();

		} catch (

		Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param absoluteFile
	 * @return
	 */
	public static boolean containValue(String value) {

		try {
			SharedPreference sp;

			File file = new File(SharedPreference.savePath);
			if (!file.exists() || file.length() == 0) {
				return false;
			}

			long length = file.length();
			RandomAccessFile raf = new RandomAccessFile(file, "rw");

			// 读出已有的内容
			byte[] bytes = new byte[(int) length];
			raf.read(bytes);
			raf.close();

			String json = new String(bytes);
			sp = JSON.parseObject(json, SharedPreference.class);
			if (sp == null) {
				return false;
			}

			HashMap<String, String> map = sp.getSpMap();
			for (String key : map.keySet()) {
				String v = map.get(key);
				if (v.equals(value)) {
					return true;
				}
			}

		} catch (

		Exception e) {
			e.printStackTrace();
		}
		return false;
	}

}
