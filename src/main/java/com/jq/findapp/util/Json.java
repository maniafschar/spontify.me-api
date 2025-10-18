package com.jq.findapp.util;

import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Json {
	private static final ObjectMapper om = new ObjectMapper();

	static {
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public static JsonNode toNode(final String json) {
		try {
			return om.readTree(json);
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static <T> T toObject(final Map<String, Object> values, final Class<T> clazz) {
		try {
			return om.convertValue(values, clazz);
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static <T> T toObject(final String json, final Class<T> clazz) {
		try {
			return om.readValue(json, clazz);
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toString(final Object object) {
		try {
			return om.writeValueAsString(object);
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toPrettyString(final Object object) {
		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(object);
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static ObjectNode createObject() {
		return om.createObjectNode();
	}
}