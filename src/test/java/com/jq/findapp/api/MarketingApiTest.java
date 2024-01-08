package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MarketingApiTest {
	@Test
	public void replaceFirst() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("<meta property=\"og:url\" content=\"([^\"].*)\"",
				"<meta property=\"og:url\" content=\"xxx\"");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"xxx\"/>\n\t</body>\n</html>", result);
	}

	@Test
	public void replace_alternate() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<link rel=\"alternate\" href=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("(<link rel=\"alternate\" ([^>].*)>)", "");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t\n\t</body>\n</html>", result);
	}
}
