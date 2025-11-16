package com.jq.findapp.service.events;

import java.math.BigInteger;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ImportMunich extends Import {
	ImportMunich() {
		this.clientId = BigInteger.ONE;
		this.url = "https://www.muenchen.de";
		this.urlExternal = "https://www.muenchenticket.de/";
		this.path = "/veranstaltungen/event";

		this.regexAddress = Pattern.compile("itemprop=\"address\"(.*?)</svg>(.*?)</a>");
		this.regexAddressRef = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		this.regexTitle = Pattern.compile("<h3(.*?)>(.*?)<span>(.*?)</span>");
		this.regexDesc = Pattern.compile(" itemprop=\"description\">(.*?)</(p|div)>");
		this.regexImage = Pattern.compile("<picture(.*?)<source srcset=\"(.*?) ");

		this.regexAddressExternal = Pattern.compile("class=\"anfahrt\"(.*?)data-venue=\"(.*?)\"");
		this.regexAddressRefExternal = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		this.regexImageExternal = Pattern.compile("class=\"show_detail_images\"(.*?) src=\"(.*?)\"");

		this.regexName = Pattern.compile("itemprop=\"location\"(.*?)</svg>(.*?)</");
		this.regexPrice = Pattern.compile("Tickets sichern ab &euro; (.*?)</");
		this.regexNextPage = Pattern.compile("<li(.*?)m-pagination__item--next-page(.*?)href=\"(.*?)\"");
		this.regexLink = Pattern.compile("<a (.*?)href=\"(.*?)\"");
		this.regexDatetime = Pattern.compile("<time (.*?)datetime=\"(.*?)\"");
	}
}