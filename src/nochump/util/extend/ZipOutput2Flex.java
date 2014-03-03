/**
 * Copyright (c) 2009 David Yao (xiang.okay@gmail.com)
 * description: Crypt the file
 * author: David Yao
 * mail: xiang.okay@gmail.com
 */
package nochump.util.extend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import nochump.util.zip.EncryptZipEntry;
import nochump.util.zip.EncryptZipOutput;;

public final class ZipOutput2Flex {

	  public static byte[] getEncryptZipByte(File[] srcfile,String password) {
		  ByteArrayOutputStream tempOStream = new ByteArrayOutputStream(1024);
			byte[] tempBytes = null;
			byte[] buf = new byte[1024];
			try {
				EncryptZipOutput out = new EncryptZipOutput(tempOStream,password);
				for (int i = 0; i < srcfile.length; i++) {
					out.putNextEntry(new EncryptZipEntry(srcfile[i].getName()));

					FileInputStream in = new FileInputStream(srcfile[i]);
					int len;
					while ((len = in.read(buf)) > 0) {
						out.write(buf, 0, len);
					}
					out.closeEntry();
					in.close();
				}
				tempOStream.flush();
				out.close();
				tempBytes = tempOStream.toByteArray();
				tempOStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return tempBytes;
	  }
	  
}
