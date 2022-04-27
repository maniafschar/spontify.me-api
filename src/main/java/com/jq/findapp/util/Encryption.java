package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import org.apache.commons.io.IOUtils;

public class Encryption {
	private static final PublicKey publicDB;
	private static final PrivateKey privateDB;
	private static final PrivateKey privateBrowser;
	private static final String algorithm = "RSA";

	static {
		try {
			final KeyFactory kf = KeyFactory.getInstance(algorithm);
			publicDB = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder()
					.decode(IOUtils.toByteArray(Encryption.class.getResourceAsStream("/keys/publicDB.key")))));
			privateDB = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
					.decode(IOUtils.toByteArray(Encryption.class.getResourceAsStream("/keys/privateDB.key")))));
			// public key is in js/communication.js
			privateBrowser = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
					.decode(IOUtils.toByteArray(Encryption.class.getResourceAsStream("/keys/privateBrowser.key")))));
		} catch (Exception e) {
			throw new RuntimeException("Failed to init encryption keys", e);
		}
	}

	public static String encrypt(final String s, final String publicKey) {
		try {
			final KeyFactory kf = KeyFactory.getInstance(algorithm);
			final byte[] key = Base64.getDecoder().decode(publicKey);
			final Cipher enc = Cipher.getInstance(algorithm);
			enc.init(Cipher.ENCRYPT_MODE, kf.generatePublic(new X509EncodedKeySpec(key)));
			return new String(Base64.getEncoder().encode(enc.doFinal(s.getBytes(StandardCharsets.UTF_8))));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to encrypt", ex);
		}
	}

	public static String encryptDB(final String s) {
		try {
			final Cipher encryptDB = Cipher.getInstance(algorithm);
			encryptDB.init(Cipher.ENCRYPT_MODE, publicDB);
			return new String(Base64.getEncoder().encode(encryptDB.doFinal(s.getBytes(StandardCharsets.UTF_8))));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to encrypt", ex);
		}
	}

	public static String decryptDB(final String s) {
		try {
			final Cipher decryptDB = Cipher.getInstance(algorithm);
			decryptDB.init(Cipher.DECRYPT_MODE, privateDB);
			return new String(decryptDB.doFinal(Base64.getDecoder().decode(s)), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Failed to decrypt", e);
		}
	}

	public static String decryptBrowser(final String s) {
		try {
			final Cipher decryptBrowser = Cipher.getInstance(algorithm);
			decryptBrowser.init(Cipher.DECRYPT_MODE, privateBrowser);
			return new String(decryptBrowser.doFinal(Base64.getDecoder().decode(s)), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Failed to decrypt", e);
		}
	}
}