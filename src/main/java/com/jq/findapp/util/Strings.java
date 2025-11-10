package com.jq.findapp.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.springframework.web.util.HtmlUtils;

public class Strings {
	public static final Pattern EMAIL = Pattern.compile("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6})",
			Pattern.CASE_INSENSITIVE);
	public static final String TIME_OFFSET = "Europe/Berlin";
	private static final SSLContext sslContext;
	static {
		try {
			sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, new TrustManager[] { new X509ExtendedTrustManager() {
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return new java.security.cert.X509Certificate[0];
				}

				@Override
				public void checkServerTrusted(final java.security.cert.X509Certificate[] chain, final String authType)
						throws CertificateException {
				}

				@Override
				public void checkClientTrusted(final X509Certificate[] chain, final String authType)
						throws CertificateException {
				}

				@Override
				public void checkClientTrusted(final X509Certificate[] chain, final String authType,
						final Socket socket)
						throws CertificateException {
				}

				@Override
				public void checkServerTrusted(final X509Certificate[] chain, final String authType,
						final Socket socket)
						throws CertificateException {
				}

				@Override
				public void checkClientTrusted(final X509Certificate[] chain, final String authType,
						final SSLEngine engine)
						throws CertificateException {
				}

				@Override
				public void checkServerTrusted(final X509Certificate[] chain, final String authType,
						final SSLEngine engine)
						throws CertificateException {
				}
			} }, new SecureRandom());
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

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
		return date.toInstant().atZone(TimeZone.getTimeZone(isEmpty(zone) ? TIME_OFFSET : zone).toZoneId())
				.format(DateTimeFormatter.ofPattern(format == null ? "dd.MM.yyyy HH:mm" : format));
	}

	public static String sanitize(String s, final int limit) {
		if (s == null)
			return s;
		s = HtmlUtils.htmlUnescape(s.replaceAll("<li>", "\n* ")
				.replaceAll("<[^>]*>", "\n")
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

	public static String urlContent(final String url) {
		try {
			return (String) HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(10))
					.build().send(HttpRequest.newBuilder()
							.uri(URI.create(url))
							.timeout(Duration.ofSeconds(15))
							.header("Content-Type", "application/json")
							.header("accept",
									"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
							.header("accept-language", "de-DE,de;q=0.9,en;q=0.8,en-US;q=0.7")
							.GET()
							.build(), BodyHandlers.ofString())
					.body();
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
