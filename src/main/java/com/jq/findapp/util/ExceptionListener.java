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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService.AuthenticationException;
import com.jq.findapp.service.AuthenticationService.AuthenticationException.AuthenticationExceptionType;
import com.jq.findapp.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;

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
		final HttpStatus statusCode = ex instanceof AuthenticationException ? HttpStatus.UNAUTHORIZED
				: HttpStatus.INTERNAL_SERVER_ERROR;
		final Map<String, Object> body = new HashMap<>(3);
		body.put("msg", ex.getMessage());
		body.put("class", ex.getClass().getSimpleName());
		body.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		report(((ServletWebRequest) request).getRequest(), ex, statusCode);
		return createResponseEntity(body, null, statusCode, request);
	}

	private void report(HttpServletRequest request, Exception ex, HttpStatus status) {
		if (ex instanceof AuthenticationException
				&& ((AuthenticationException) ex).getType() == AuthenticationExceptionType.WrongPassword)
			return;
		String msg = status + "\n\n" + request.getMethod() + " "
				+ (request.getHeader("user") == null ? "" : request.getHeader("user") + "@")
				+ SUBSTITUTE + ":" + request.getRequestURI() + getQueryString(request) + SUBSTITUTE
				+ Strings.stackTraceToString(ex);
		if (msg.indexOf("500 INTERNAL_SERVER_ERROR") == 0 && msg.indexOf(":/support/import/location/") > 0
				&& (msg.indexOf("ConstraintViolationException") > 0 || msg.indexOf("location exists") > 0))
			return;
		final Integer hash = msg.hashCode();
		synchronized (SENT_ERRORS) {
			if (SENT_ERRORS.contains(hash))
				return;
			SENT_ERRORS.add(hash);
		}
		final StringBuilder s = new StringBuilder(
				new SimpleDateFormat("\n\ndd.MM.yyyy HH:mm:ss\n").format(new Date()));
		BigInteger userId = null;
		if (request.getHeader("user") != null && !"0".equals(request.getHeader("user"))) {
			final Contact user = repository.one(Contact.class, new BigInteger(request.getHeader("user")));
			if (user == null)
				s.append("\nUSER\nid: " + request.getHeader("user") + " NOT FOUND");
			else {
				s.append("\nUSER\nid: " + user.getId());
				s.append("\nversion: " + user.getVersion());
				s.append("\ndevice: " + user.getDevice());
				s.append("\nos: " + user.getOs());
				userId = user.getId();
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
		if (s.indexOf("x-forwarded-host: findapp.online") > 0)
			return;
		msg = msg.replaceFirst(SUBSTITUTE, "" + request.getServerPort()).replaceFirst(SUBSTITUTE, s.toString());
		try {
			notificationService.createTicket(TicketType.ERROR, "uncaught exception", msg, userId);
		} catch (Exception e1) {
			notificationService.createTicket(TicketType.ERROR, "uncaught exception, error on creating report",
					msg + "\n\n\n" + Strings.stackTraceToString(e1), userId);
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
				if (!query.startsWith("_="))
					return "?" + URLDecoder.decode(query, StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		return "";
	}
}
