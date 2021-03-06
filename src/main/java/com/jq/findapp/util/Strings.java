package com.jq.findapp.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Strings {
	public static final String URL = "https://spontify.me";

	public static String encodeParam(final String param) {
		int x = 0;
		for (int i = 2; i < param.length(); i++) {
			if (param.charAt(i) >= '0' && param.charAt(i) <= '9')
				x += (param.charAt(i) - '0');
		}
		return Base64.getEncoder().encodeToString(param.getBytes(StandardCharsets.UTF_8)) + '=' + x;
	}

	public static boolean isEmpty(final Object s) {
		return s == null || s.toString().trim().length() == 0;
	}

	public static String stackTraceToString(Throwable ex) {
		if (ex == null)
			return "";
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ex.printStackTrace(new PrintStream(baos));
		String s = new String(baos.toByteArray());
		if (s.indexOf(ex.getClass().getName()) < 0)
			s = ex.getClass().getName() + ": " + s;
		return s.replaceAll("\r", "").replaceAll("\n\n", "\n");
	}

	public static StringBuilder replaceString(final StringBuilder s, final String from, String to) {
		if (from != null && s != null && from.length() > 0) {
			if (to == null)
				to = "";
			int pos = 0;
			while ((pos = s.indexOf(from, pos)) > -1) {
				s.replace(pos, pos + from.length(), to);
				pos += to.length();
			}
		}
		return s;
	}
}