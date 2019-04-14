package com.uestc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RAFTest {

	public static void main(String[] args) {

		try {
			File file = new File("G:\\20190125-6.zip");
			RandomAccessFile raf = new RandomAccessFile(file, "rw");

			File temp = new File("G:\\writer.zip");
			temp.createNewFile();
			RandomAccessFile writer = new RandomAccessFile(temp, "rw");

			writer.seek(0);
			byte[] buffer = new byte[1024];
			for (int i = 0; i < 1024 * 30; i++) {
				raf.read(buffer);
				writer.write(buffer);
			}

			raf.close();
			writer.close();

//			
			File file2 = new File("G:\\20190125-6.zip");
			RandomAccessFile raf2 = new RandomAccessFile(file2, "rw");

			File temp2 = new File("G:\\writer.zip");
			RandomAccessFile writer2 = new RandomAccessFile(temp2, "rw");

			byte[] buffer2 = new byte[1024];

			raf2.seek(writer2.length());
			writer2.seek(writer2.length());
			while (raf2.read(buffer2) != -1) {
				writer2.write(buffer2);
			}

			raf2.close();
			writer2.close();
			
			File t3=new File("G:\\writer.zip");
			t3.renameTo(new File("G:\\w4"));

			System.out.println("close++++++++++++++");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
