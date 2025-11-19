package com.jq.findapp.service.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.util.Strings;

@Component
public class ImportService {
	@Autowired
	private ImportMunich importMunich;

	@Autowired
	private ImportKempten importKempten;

	private UrlFetcher urlFetcher = new UrlFetcher();

	public class UrlFetcher {
		public String get(final String url) {
			return Strings.urlContent(url)
					.replace('\n', ' ')
					.replace('\r', ' ')
					.replace('\u0013', ' ')
					.replace('\u001c', ' ')
					.replace('\u001e', ' ');
		}
	}

	public void setUrlFetcher(final UrlFetcher urlFetcher) {
		this.urlFetcher = urlFetcher;
	}

	public String run() throws Exception {
		return "M: " + this.importMunich.run(this.urlFetcher);
		// + "\nKE: " + this.importKempten.run(urlFetcher);
	}

}