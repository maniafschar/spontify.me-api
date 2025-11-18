package com.jq.findapp.service.event;

import java.math.BigInteger;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class ImportMunich extends Import {
	ImportMunich() {
		this.clientId = BigInteger.ONE;
		this.url = "https://www.muenchen.de";
		this.urlExternal = "https://www.muenchenticket.de/";
		this.path = "/veranstaltungen/event";

		this.regexListStartTag = Pattern.compile("<li class=\"m-listing__list-item\">");
		this.regexListEndTag = Pattern.compile("</li>");
		this.regexLink = Pattern.compile("<a (.*?)href=\"(.*?)\"");
		this.regexNextPage = Pattern.compile("<li(.*?)m-pagination__item--next-page(.*?)href=\"(.*?)\"");

		this.regexTitle = Pattern.compile("<h3(.*?)>(.*?)<span>(.*?)</span>");
		this.regexDesc = Pattern.compile(" itemprop=\"description\">(.*?)</(p|div)>");
		this.regexPrice = Pattern.compile("Tickets sichern ab &euro; (.*?)</");
		this.regexImage = Pattern.compile("<picture(.*?)<source srcset=\"(.*?) ");
		this.regexDatetime = Pattern.compile("<time (.*?)datetime=\"(.*?)\"");

		this.regexName = Pattern.compile("itemprop=\"location\"(.*?)</svg>(.*?)</");
		this.regexAddress = Pattern.compile("itemprop=\"address\"(.*?)</svg>(.*?)</a>");
		this.regexAddressRef = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");

		this.regexAddressExternal = Pattern.compile("class=\"anfahrt\"(.*?)data-venue=\"(.*?)\"");
		this.regexAddressRefExternal = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		this.regexImageExternal = Pattern.compile("class=\"show_detail_images\"(.*?) src=\"(.*?)\"");
	}
}
