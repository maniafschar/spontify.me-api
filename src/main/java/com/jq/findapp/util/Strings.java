package com.jq.findapp.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

public class Strings {
	public static final Pattern EMAIL = Pattern.compile("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6})",
			Pattern.CASE_INSENSITIVE);
	public static final String TIME_OFFSET = "Europe/Berlin";

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

	public static boolean isEmail(final String email) {
		return EMAIL.matcher(email).replaceAll("").length() == 0;
	}

	public static String removeSubdomain(final String url) {
		final String[] u = url.split("\\.");
		if (u.length > 2)
			return u[0].substring(0, u[0].indexOf("://") + 3) + u[1] + "." + u[2];
		return url;
	}

	public static String stackTraceToString(final Throwable ex) {
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

	public static String formatDate(final String format, final Date date, final String zone) {
		return date.toInstant().atZone(TimeZone.getTimeZone(zone).toZoneId())
				.format(DateTimeFormatter.ofPattern(format == null ? "dd.MM.yyyy HH:mm" : format));
	}

	public static String sanitize(String s, final int limit) {
		if (s == null)
			return s;
		s = HtmlUtils.htmlUnescape(s.replaceAll("<[^>]*>", "\n")
				.replace("&nbsp;", " ")
				.replace("\t", " ")).trim();
		while (s.contains("  "))
			s = s.replace("  ", " ");
		while (s.contains("\n\n"))
			s = s.replace("\n\n", "\n");
		while (s.contains("\n "))
			s = s.replace("\n ", "\n");
		if (limit > 0 && s.length() > limit)
			s = s.substring(0, s.substring(0, limit - 3).lastIndexOf(' ')) + "...";
		return s.trim();
	}

	public static String generatePin(final int length) {
		final StringBuilder s = new StringBuilder();
		char c;
		while (s.length() < length) {
			c = (char) (Math.random() * 150);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
				s.append(c);
		}
		return s.toString();
	}
}
