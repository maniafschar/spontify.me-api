package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EventServiceTest {
	@Autowired
	private EventService eventService;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void importEvents() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate='2023-12-02 09:00:00'");

		// when
		final SchedulerResult result = eventService.importEvents();

		// then
		assertNull(result.exception);
		assertEquals("Munich: 56 imported, 0 published", result.result);
		assertEquals(1, repository.list(params).size());
	}

	@Test
	public void importEvents_error() throws Exception {
		// given
		String page = IOUtils.toString(getClass().getResourceAsStream("/html/eventError.html"), StandardCharsets.UTF_8);
		page = page.replace('\n', ' ').replace('\r', ' ').replace('\u0013', ' ');
		page = page.substring(page.indexOf("<ul class=\"m-listing__list\""));
		page = page.substring(0, page.indexOf("</ul>") + 5);
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new InputSource(new StringReader(page)));

		// when
		doc.getElementsByTagName("li");

		// then no exception
	}

	@Test
	public void decodeHtmlEntities() {
		// given
		String s = "Bamberger Symphoniker\nCherubini: Ouvert&uuml;re zu &bdquo;Medea&ldquo;     Beethoven: Konzert f&uuml;r Klavier und Orchester Nr. 2 B-Dur op. 19     Strawinsky: &bdquo;Le sacre du printemps&ldquo;         Mitsuko Uchida, Klavier   Jakub Hru&scaron;a, Leitung       Leonard Bernstein  galt ihm als musikalisches Idol &ndash; inzwischen ist  Jakub Hrů&scaron;a  selbst ein Vorbild f&uuml;r den Nachwuchs am Pult. &bdquo;Exzess mit Understatement&ldquo;, so beschreibt das Klassikmagazin Rondo den bescheidenen Tschechen. Parallel zu seinem Amt als Chefdirigent der Bamberger Symphoniker ist  Hrů&scaron;a  designierter Musikdirektor des Royal Opera House in London, erster Gastdirigent der Tschechischen Philharmonie sowie des Orchestra dell&rsquo;Accademia Nazionale di Santa Cecilia.      Im Dezember ist  Hrů&scaron;a  mit den Bamberger Symphonikern und  Mitsuko Uchida  zu Gast. Die japanische Pianistin ist bekannt f&uuml;r &bdquo;ihre klangliche Sensibilit&auml;t und ihren gro&szlig;en An...\nGasteig HP8, Isarphilharmonie\nHans-Preißinger-Str. 8\n81379 München";

		// when
		s = HtmlUtils.htmlUnescape(s);

		// then
		assertEquals(
				"Bamberger Symphoniker\nCherubini: Ouvertüre zu „Medea“     Beethoven: Konzert für Klavier und Orchester Nr. 2 B-Dur op. 19     Strawinsky: „Le sacre du printemps“         Mitsuko Uchida, Klavier   Jakub Hruša, Leitung       Leonard Bernstein  galt ihm als musikalisches Idol – inzwischen ist  Jakub Hrůša  selbst ein Vorbild für den Nachwuchs am Pult. „Exzess mit Understatement“, so beschreibt das Klassikmagazin Rondo den bescheidenen Tschechen. Parallel zu seinem Amt als Chefdirigent der Bamberger Symphoniker ist  Hrůša  designierter Musikdirektor des Royal Opera House in London, erster Gastdirigent der Tschechischen Philharmonie sowie des Orchestra dell’Accademia Nazionale di Santa Cecilia.      Im Dezember ist  Hrůša  mit den Bamberger Symphonikern und  Mitsuko Uchida  zu Gast. Die japanische Pianistin ist bekannt für „ihre klangliche Sensibilität und ihren großen An...\nGasteig HP8, Isarphilharmonie\nHans-Preißinger-Str. 8\n81379 München",
				s);
	}
}