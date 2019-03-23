package com.uestc.test;

import com.uestc.net.util.SharedPreferenceUtil;

public class Test {

	@org.junit.Test
	public void testSave() {

		 SharedPreferenceUtil.save("file.txt", "xxxxxxxxxxxxxx");
		
	}
	
	@org.junit.Test
	public void testRemove() {
		
		System.out.println(SharedPreferenceUtil.get("file.txt"));

	}

}
