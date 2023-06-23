package com.jq.findapp.util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.backend.IpService;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(1)
public class LogFilter implements Filter {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.supportCenter.secret}")
	private String supportCenterSecret;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException {
		final ContentCachingRequestWrapper req = new ContentCachingRequestWrapper((HttpServletRequest) request);
		final HttpServletResponse res = (HttpServletResponse) response;
		final Log log = new Log();
		log.setWebCall(req.getHeader("webCall"));
		authenticate(req);
		final boolean loggable = !"OPTIONS".equals(req.getMethod()) && !"/action/ping".equals(req.getRequestURI());
		if (loggable) {
			log.setUri(req.getRequestURI());
			log.setMethod(req.getMethod());
			if (req.getHeader("referer") != null) {
				log.setReferer(req.getHeader("referer"));
				if (log.getReferer().length() > 255)
					log.setReferer(log.getReferer().substring(0, 255));
			}
			log.setIp(IpService.sanatizeIp(req.getHeader("X-Forwarded-For")));
			log.setPort(req.getLocalPort());
			if (req.getHeader("user") != null)
				log.setContactId(new BigInteger(req.getHeader("user")));
			final String query = req.getQueryString();
			if (query != null) {
				if (query.contains("&_="))
					log.setQuery(URLDecoder.decode(query.substring(0, query.indexOf("&_=")),
							StandardCharsets.UTF_8.name()));
				else if (!query.startsWith("_="))
					log.setQuery(URLDecoder.decode(query, StandardCharsets.UTF_8.name()));
				if (log.getQuery() != null && log.getQuery().length() > 255)
					log.setQuery(log.getQuery().substring(0, 255));
			}
		}
		final long time = System.currentTimeMillis();
		try {
			chain.doFilter(req, res);
		} finally {
			if (loggable && (!"/support/healthcheck".equals(log.getUri()) || res.getStatus() >= 400)) {
				log.setTime((int) (System.currentTimeMillis() - time));
				log.setStatus(res.getStatus());
				log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli() - log.getTime()));
				final byte[] b = req.getContentAsByteArray();
				if (b != null && b.length > 0) {
					log.setBody(new String(b, StandardCharsets.UTF_8));
					if (log.getBody().length() > 255)
						log.setBody(log.getBody().substring(0, 255));
				}
				try {
					repository.save(log);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void authenticate(final ContentCachingRequestWrapper req) {
		if (req.getHeader("user") != null) {
			final BigInteger user = new BigInteger(req.getHeader("user"));
			if (!BigInteger.ZERO.equals(user)) {
				final Contact contact = authenticationService.verify(user,
						req.getHeader("password"), req.getHeader("salt"));
				if (contact.getClientId().intValue() != Integer.parseInt(req.getHeader("clientId")))
					throw new RuntimeException("Invalid client header " + req.getHeader("clientId")
							+ ", expected in contact: " + contact.getClientId());
			}
		} else if (!"OPTIONS".equals(req.getMethod()) && req.getRequestURI().startsWith("/support/")) {
			if (supportCenterSecret.equals(req.getHeader("secret")))
				authenticationService.verify(adminId, req.getHeader("password"), req.getHeader("salt"));
			else if (!schedulerSecret.equals(req.getHeader("secret")) ||
					!req.getRequestURI().equals("/support/scheduler") &&
							!req.getRequestURI().equals("/support/healthcheck"))
				throw new RuntimeException("Invalid access to " + req.getRequestURI());
		}
	}
}