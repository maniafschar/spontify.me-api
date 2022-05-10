package com.jq.findapp.service.push;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtGenerator {
	private static Map<String, String> token = new HashMap<>();
	private static final long TIMEOUT = 3300000;
	private static Map<String, PrivateKey> signingKey = new HashMap<>();

	@Autowired
	private Repository repository;

	protected long getLastGeneration(String keyId) throws Exception {
		final String label = "push.gen." + keyId;
		long lastGeneration;
		final QueryParams param = new QueryParams(Query.misc_setting);
		param.setSearch("setting.label='" + label + "'");
		final Map<String, Object> settingMap = repository.one(param);
		final Setting setting;
		if (settingMap == null) {
			setting = new Setting();
			setting.setLabel(label);
			lastGeneration = 0;
		} else {
			setting = repository.one(Setting.class, (BigInteger) settingMap.get("setting.id"));
			lastGeneration = Long.valueOf(setting.getValue().toString());
		}
		if (System.currentTimeMillis() - lastGeneration > TIMEOUT) {
			lastGeneration = System.currentTimeMillis();
			setting.setValue("" + lastGeneration);
			repository.save(setting);
			token.remove(keyId);
		}
		return lastGeneration;
	}

	protected String generateToken(Map<String, String> header, Map<String, String> claims, String algorithm)
			throws Exception {
		final String keyId = header.get("kid");
		if (!token.containsKey(keyId)) {
			final StringBuilder result = new StringBuilder();
			result.append(jwtEncoding(new ObjectMapper().writeValueAsString(header).getBytes(StandardCharsets.UTF_8)));
			result.append('.');
			result.append(jwtEncoding(new ObjectMapper().writeValueAsString(claims).getBytes(StandardCharsets.UTF_8)));
			final PrivateKey key = getSigningKey(keyId, algorithm);
			final Signature signature = Signature
					.getInstance("RSA".equals(algorithm) ? "SHA256withRSA" : "SHA256withECDSA");
			signature.initSign(key);
			signature.update(result.toString().getBytes(StandardCharsets.UTF_8));
			result.append('.');
			result.append(jwtEncoding(signature.sign()));
			token.put(keyId, result.toString());
		}
		return token.get(keyId);
	}

	private String jwtEncoding(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private PrivateKey getSigningKey(String keyId, String algorithm)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		if (!signingKey.containsKey(keyId)) {
			synchronized (Ios.class) {
				if (!signingKey.containsKey(keyId)) {
					final StringBuilder privateKeyBuilder = new StringBuilder();
					final BufferedReader reader = new BufferedReader(
							new InputStreamReader(
									getClass().getResourceAsStream("/keys/push_" + keyId + ".p8")));
					for (String line; (line = reader.readLine()) != null;) {
						if (line.contains("END PRIVATE KEY"))
							break;
						else if (!line.contains("BEGIN PRIVATE KEY"))
							privateKeyBuilder.append(line);
					}
					final byte[] keyBytes = Base64.getDecoder().decode(privateKeyBuilder.toString().trim());
					final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
					final KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
					signingKey.put(keyId, keyFactory.generatePrivate(keySpec));
				}
			}
		}
		return signingKey.get(keyId);
	}
}
