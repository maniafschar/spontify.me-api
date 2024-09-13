package com.jq.findapp.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Json {
	private static final ObjectMapper om = new ObjectMapper();

	static {
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public static JsonNode toNode(String json) {
		try {
			return om.readTree(json);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static <T> T toObject(String json, Class<T> class) {
		try {
			return om.readValue(json, class);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toString(Object object) {
		try {
			return om.writeValueAsString(object);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toPrettyString(Object object) {
		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(object);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
