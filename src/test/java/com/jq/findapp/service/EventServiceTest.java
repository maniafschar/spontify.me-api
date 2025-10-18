package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.events.ImportMunich;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EventServiceTest {
	@Autowired
	private EventService eventService;

	@Autowired
	private ImportMunich importMunich;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void importMunich() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate=cast('2024-06-28 09:00:00' as timestamp)");

		// when
		final int result = this.importMunich.run(this.eventService, BigInteger.ONE);

		// then
		assertEquals(27, result);
		assertEquals(1, this.repository.list(params).size());
	}

	@Test
	public void importEvents_error() throws Exception {
		// given
		String page = IOUtils.toString(this.getClass().getResourceAsStream("/html/eventError.html"),
				StandardCharsets.UTF_8);
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

	@Test
	public void sanitize() {
		// given
		final String s = "<p><strong>Alter</strong><br />\n"
				+ "ab 5 Jahren</p>\n"
				+ "\n"
				+ "<p><strong>Dauer</strong><br />\n"
				+ "ca. 90 Minuten inkl. Pause</p>\n"
				+ "\n"
				+ "<p><strong>Inhalt</strong><br />\n"
				+ "In Gr&uuml;nland beginnt ein wundersch&ouml;ner Sonnentag, den unser Tabaluga in vollen Z&uuml;gen genie&szlig;en m&ouml;chte. Faul sein, in der Sonne liegen und mit den Schmetterlingen spielen, ist sein Plan. Aber da gibt es einen, der etwas dagegen hat: Arktos, der Herrscher der Eiswelt! Mit einem gewaltigen Schnee- und Eissturm beendet Arktos den sch&ouml;nen Sonnenmorgen. Tabaluga wird von den Schneemassen versch&uuml;ttet. Mit der Ank&uuml;ndigung, er werde Gr&uuml;nland &bdquo;frosten&ldquo; verl&auml;sst Arktos triumphierend Gr&uuml;nland. Tabaluga verliert sein Ged&auml;chtnis und hat vergessen wer er ist.<br />\n"
				+ "Wie so oft im Leben, wenn man glaubt, es gibt keinen Ausweg mehr, naht die Rettung: F&uuml;r Tabaluga ist es ein Gl&uuml;cksk&auml;fer, der sich mit ihm auf eine Reise durch sein Ged&auml;chtnis begibt.<br />\n"
				+ "&nbsp;</p>\n";

		// when
		String result = HtmlUtils.htmlUnescape(s
				.replaceAll("<[^>]*>", "\n")
				.replace("&nbsp;", " ")
				.replace("\t", " ")).trim();
		while (result.contains("  "))
			result = result.replace("  ", " ");
		while (result.contains("\n\n"))
			result = result.replace("\n\n", "\n");

		// then
		assertEquals("Alter\n"
				+ "ab 5 Jahren\n"
				+ "Dauer\n"
				+ "ca. 90 Minuten inkl. Pause\n"
				+ "Inhalt\n"
				+ "In Grünland beginnt ein wunderschöner Sonnentag, den unser Tabaluga in vollen Zügen genießen möchte. Faul sein, in der Sonne liegen und mit den Schmetterlingen spielen, ist sein Plan. Aber da gibt es einen, der etwas dagegen hat: Arktos, der Herrscher der Eiswelt! Mit einem gewaltigen Schnee- und Eissturm beendet Arktos den schönen Sonnenmorgen. Tabaluga wird von den Schneemassen verschüttet. Mit der Ankündigung, er werde Grünland „frosten“ verlässt Arktos triumphierend Grünland. Tabaluga verliert sein Gedächtnis und hat vergessen wer er ist.\n"
				+ "Wie so oft im Leben, wenn man glaubt, es gibt keinen Ausweg mehr, naht die Rettung: Für Tabaluga ist es ein Glückskäfer, der sich mit ihm auf eine Reise durch sein Gedächtnis begibt.",
				result);
	}

	@Test
	public void duplicateEvents() throws Exception {
		// given
		final JsonNode events = Json.toNode(IOUtils
				.toString(this.getClass().getResourceAsStream("/json/duplicateEvents.json"), StandardCharsets.UTF_8));
		final Set<String> processed = new HashSet<>();
		final StringBuffer result = new StringBuffer();

		// when
		for (int i = 0; i < events.size(); i++) {
			final String key = events.get(i).get("CONTACT_ID").asText() + "-"
					+ events.get(i).get("LOCATION_ID").asText() + "-"
					+ events.get(i).get("series_id").asText() + "-"
					+ events.get(i).get("description").asText();
			if (processed.contains(key))
				result.append("delete from event where id=" + events.get(i).get("id").asText() + ";\n");
			else
				processed.add(key);
		}

		// then
		assertEquals("", result.toString());
	}
}