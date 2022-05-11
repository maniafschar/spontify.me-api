package com.jq.findapp.util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@Order(1)
public class LogFilter implements Filter {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse res = (HttpServletResponse) response;
		final Log log = new Log();
		if (!"/action/ping".equals(req.getRequestURI())) {
			log.setUri(req.getRequestURI());
			log.setMethod(req.getMethod());
			log.setPort(req.getServerPort());
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
			try {
				repository.save(log);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		final long time = System.currentTimeMillis();
		try {
			chain.doFilter(new ContentCachingRequestWrapper(req), res);
		} finally {
			if (!"/action/ping".equals(req.getRequestURI())) {
				log.setTime((int) (System.currentTimeMillis() - time));
				log.setStatus(res.getStatus());
				try {
					repository.save(log);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}