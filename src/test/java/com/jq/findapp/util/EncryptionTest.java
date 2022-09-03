package com.jq.findapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

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

	@Test
	public void decryptBrowser() {
		// given

		// when
		final String email = Encryption.decryptBrowser(
				"FX2qb7TvKnbG3WRb6BZA4XNpfPHQ11+lbc65L4Pqtdf5wIUZiMLX1H/HGkqdcq7qFIRPoxNKmPRc/onxK6ywqp+z1b5hNojfVU7rwnmei7RDMDWKZH0HlHBIWW6fvsMwiK+8iG2JD90bkQSyt9AWI4c0FMdJKhYQprbj2r/f4TdKDtN8ADChclXPfLts7o6Z1AlfTuFF6v8VjS0nJrJBkbjiBH7o45D7FvmFywB0qMsJ4BC2c5x+Qtl5SCvm+YPpAY5zuiTfjceOk+Ngjk/qsUemT4HDGeC3rE+NQ5of/drdFMqC1o0LF86fiQAzucC4SeKEtlL49s1UV83UeL8rgs51wFuX2MtRjGh2978yrsXW+GZqVAIbg7uxF89Bkyfg6uYxVnnRvk8f/WHq7lim3CexB+mjW4YTuwrOBMOtp5E/lH8Ghf2odH+z7v/Dy7XSkzzPlvtEJTqerUV5e855l50IVE0DQPnAhhFLt9A9bLxIz+7cTlolsbiank2ZW6LPeFU7ESJ4npkaPf4DEbpt2NwjVRx2tBwehqxNSyZ+cO/IFgf3BD/U2PKmlL8vmaiWQa7sztKgg60GANH0WYaR3vy/zc5oOMOkQWOzu2/sS7K29YRERzv0fuzebhETV/4TMFkcWM6FG4Tb5DcfPvttmV5e80NXRP4XXbVgGP/OdJs=");

		// then
		assertEquals("denniswinzer@gmx.de", email);
	}
}