package com.jq.findapp.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService.AuthenticationException;
import com.jq.findapp.service.AuthenticationService.AuthenticationException.Type;
import com.jq.findapp.service.NotificationService;

@RestControllerAdvice
public class ExceptionListener extends ResponseEntityExceptionHandler {
	private static final List<Integer> SENT_ERRORS = new ArrayList<>();
	private static final String SUBSTITUTE = "\u0015";

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handle(Exception ex, WebRequest request) {
		final Map<String, Object> body = new HashMap<>();
		body.put("exception", Strings.stackTraceToString(ex));
		body.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		return handleExceptionInternal(ex, body, null,
				ex instanceof AuthenticationException ? HttpStatus.FORBIDDEN : HttpStatus.INTERNAL_SERVER_ERROR,
				request);
	}

	@Override
	public ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatus status, WebRequest request) {
		report(((ServletWebRequest) request).getRequest(), ex, status);
		return super.handleExceptionInternal(ex, body, headers, status, request);
	}

	private void report(HttpServletRequest request, Exception ex, HttpStatus status) {
		if (ex instanceof AuthenticationException && ((AuthenticationException) ex).getType() == Type.WrongPassword)
			return;
		String msg = status + "\n\n" + request.getMethod() + " "
				+ (request.getHeader("user") == null ? "" : request.getHeader("user") + "@")
				+ SUBSTITUTE + ":" + request.getRequestURI() + getQueryString(request) + SUBSTITUTE
				+ Strings.stackTraceToString(ex);
		final Integer hash = msg.hashCode();
		boolean send = false;
		synchronized (SENT_ERRORS) {
			if (!SENT_ERRORS.contains(hash))
				send = SENT_ERRORS.add(hash);
		}
		if (send) {
			final StringBuilder s = new StringBuilder(
					new SimpleDateFormat("\n\ndd.MM.yyyy HH:mm:ss\n").format(new Date()));
			if (request.getHeader("user") != null && !"0".equals(request.getHeader("user"))) {
				final Contact user = repository.one(Contact.class, new BigInteger(request.getHeader("user")));
				if (user == null)
					s.append("\nUSER\nid: " + request.getHeader("user") + " NOT FOUND");
				else {
					s.append("\nUSER\nid: " + user.getId());
					s.append("\nversion: " + user.getVersion());
					s.append("\ndevice: " + user.getDevice());
					s.append("\nos: " + user.getOs());
				}
				s.append("\n");
			}
			s.append("\nHEADER\n");
			List<String> list = Collections.list(request.getHeaderNames());
			Collections.sort(list);
			for (String n : list)
				s.append(n + ": " + request.getHeader(n) + "\n");
			s.append("\n");
			if (request.getParameterMap().size() > 0) {
				s.append("\nPARAM\n");
				list = Collections.list(request.getParameterNames());
				Collections.sort(list);
				for (String n : list)
					s.append(n + ": " + request.getParameter(n) + "\n");
				s.append("\n");
			}
			if (request instanceof ContentCachingRequestWrapper) {
				final byte[] b = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
				if (b != null && b.length > 0) {
					s.append("\nREQUEST BODY\n");
					s.append(new String(b, StandardCharsets.UTF_8));
					s.append("\n\n");
				}
			}
			try {
				msg = msg.replaceFirst(SUBSTITUTE, "" + request.getServerPort()).replaceFirst(SUBSTITUTE, s.toString());
				notificationService.sendEmail(null, "ERROR " + ex.getMessage(), msg);
			} catch (Exception e1) {
				// never happend in 22 years...
			}
		}
	}

	private String getQueryString(HttpServletRequest request) {
		String query = request.getQueryString();
		if (query != null) {
			try {
				query = query.replace(SUBSTITUTE, "");
				if (query.contains("&_="))
					return "?" + URLDecoder.decode(query.substring(0, query.indexOf("&_=")),
							StandardCharsets.UTF_8.name());
				else if (!query.startsWith("_="))
					return "?" + URLDecoder.decode(query, StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		return "";
	}
}
