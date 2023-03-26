package com.jq.findapp.util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Repository;

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
	private Pattern referer = null;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (referer == null)
			referer = Pattern.compile("(https://([a-z]*.)?skillvents.com|http[s]?://localhost).*");
		final ContentCachingRequestWrapper req = new ContentCachingRequestWrapper((HttpServletRequest) request);
		final HttpServletResponse res = (HttpServletResponse) response;
		final Log log = new Log();
		log.setWebCall(req.getHeader("webCall"));
		final boolean loggable = !"OPTIONS".equals(req.getMethod()) && !"/action/ping".equals(req.getRequestURI());
		if (loggable) {
			log.setUri(req.getRequestURI());
			log.setMethod(req.getMethod());
			if (req.getHeader("Referer") != null && !referer.matcher(req.getHeader("Referer")).matches()) {
				log.setReferer(req.getHeader("Referer"));
				if (log.getReferer().length() > 255)
					log.setReferer(log.getReferer().substring(0, 255));
			}
			log.setIp(req.getHeader("X-Forwarded-For"));
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
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}