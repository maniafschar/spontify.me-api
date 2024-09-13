package com.jq.findapp.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Json {
	private static final ObjectMapper om = new ObjectMapper();

	static {
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public static JsonNode toNode(final String json) {
		try {
			return om.readTree(json);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static <T> T toObject(final String json, final Class<T> clazz) {
		try {
			return om.readValue(json, clazz);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toString(final Object object) {
		try {
			return om.writeValueAsString(object);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toPrettyString(final Object object) {
		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(object);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
