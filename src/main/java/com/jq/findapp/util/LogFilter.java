package com.jq.findapp.util;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@Order(1)
public class LogFilter implements Filter {
	private static Logger LOG = LoggerFactory.getLogger(LogFilter.class);

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final long time = System.currentTimeMillis();
		try {
			chain.doFilter(new ContentCachingRequestWrapper((HttpServletRequest) request), response);
		} finally {
			final HttpServletRequest req = (HttpServletRequest) request;
			final HttpServletResponse res = (HttpServletResponse) response;
			final StringBuilder s = new StringBuilder(req.getMethod() + " " + (System.currentTimeMillis() - time));
			if (req.getHeader("user") != null)
				s.append(":" + req.getHeader("user"));
			if (res.getStatus() >= 300)
				s.append("#" + res.getStatus());
			s.append(" " + req.getServerPort() + ":" + req.getRequestURI());
			final String query = req.getQueryString();
			if (query != null) {
				if (query.contains("&_="))
					s.append("?" + URLDecoder.decode(query.substring(0, query.indexOf("&_=")),
							StandardCharsets.UTF_8.name()));
				else if (!query.startsWith("_="))
					s.append("?" + URLDecoder.decode(query, StandardCharsets.UTF_8.name()));
			}
			LOG.info(s.toString());
		}
	}
}