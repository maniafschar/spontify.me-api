package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import org.apache.commons.io.IOUtils;

public class EncryptionTest {
	private static final Cipher ENCRYPT_BROWSER;

	static {
		try {
			final String algorithm = "RSA";
			final KeyFactory kf = KeyFactory.getInstance(algorithm);
			byte[] key = Base64.getDecoder()
					.decode(IOUtils.toByteArray(EncryptionTest.class.getResourceAsStream("/keys/publicBrowser.key")));
			ENCRYPT_BROWSER = Cipher.getInstance(algorithm);
			ENCRYPT_BROWSER.init(Cipher.ENCRYPT_MODE, kf.generatePublic(new X509EncodedKeySpec(key)));
		} catch (Exception e) {
			throw new RuntimeException("Failed to init encryption keys", e);
		}
	}

	public static String encryptBrowser(final String s) {
		try {
			return new String(Base64.getEncoder().encode(ENCRYPT_BROWSER.doFinal(s.getBytes(StandardCharsets.UTF_8))));
		} catch (Exception e) {
			throw new RuntimeException("Failed to decrypt", e);
		}
	}
}