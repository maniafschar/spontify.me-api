package com.jq.findapp.service.backend;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.util.UriEncoder;

public class LocationInfos {
	private final Pattern emailPattern = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*",
			Pattern.CASE_INSENSITIVE);
	private final String data = "Hirschbergstr., 85254 Sulzemoos, Deutschland, Gut Schloss Sulzemoos|705\n" +
			"Fraunhoferstr., 80469 München, Deutschland, Hungriges Herz|706\n" +
			"Georgenstr., 80799 München, Deutschland, Ignaz|707\n" +
			"Bergmannstr., 80339 München, Deutschland, Karaovali|709\n" +
			"Königinstr., 80539 München, Deutschland, Königin 43|710\n" +
			"Thalkirchner Str., 80337 München, Deutschland, KraftAkt|711\n" +
			"St.-Anna-Str., 80538 München, Deutschland, La Stanza|712\n" +
			"Türkenstr., 80799 München, Deutschland, Laden|713\n" +
			"Oettingenstr., 80538 München, Deutschland, Leib und Seele|714\n" +
			"Pettenkoferstr., 80336 München, Deutschland, Lenz|715\n" +
			"Rosenheimer Str., 81669 München, Deutschland, Leo's Lounge|716\n" +
			"Breisacher Str., 81667 München, Deutschland, Lisboa Bar|717\n" +
			"Wörthstr., 81667 München, Deutschland, Lollo Rosso|718\n" +
			"Volkartstr., 80634 München, Deutschland, Löwengarten|719\n" +
			"Isartorplatz 4, 80331 München, Deutschland, Mamasita|720\n" +
			"Buttermelcherstr., 80469 München, Deutschland, Melcher's|721\n" +
			"Oefelestr., 81543 München, Deutschland, Miss Lilly's|722\n" +
			"Lindwurmstr., 80337 München, Deutschland, Mixto|723\n" +
			"Morassistr., 80469 München, Deutschland, München 72|724\n" +
			"Schulstr., 80634 München, Deutschland, Neuhauser|725\n" +
			"Amalienstr., 80799 München, Deutschland, newsBAR|726\n" +
			"Volkartstr., 80634 München, Deutschland, Pardi|727\n" +
			"Guldeinstr., 80339 München, Deutschland, Red Pepper|728\n" +
			"Maximilianstr., 80539 München, Deutschland, Restaurant VUE Maximilian München|729\n" +
			"Wendl-Dietrich-Str., 80634 München, Deutschland, Rick's Café|730\n" +
			"Heinrich-Böll-Str., 81829 München, Deutschland, Riemini|731\n" +
			"Rosa-Aschenbrenner-Bogen 9, 80797 München, Deutschland, Rigoletto|732\n" +
			"Rothmundstr., 80337 München, Deutschland, Rothmund|733\n" +
			"Leopoldstr., 80802 München, Deutschland, Roxy|734\n" +
			"Orffstr., 80637 München, Deutschland, Ruffini|735\n" +
			"Giselastr., 80802 München, Deutschland, Saha|736\n" +
			"Hans-Sachs-Str., 80469 München, Deutschland, Sax|737\n" +
			"Bauerstr., 80796 München, Deutschland, Scheidegger|738\n" +
			"Schellingstr., 80799 München, Deutschland, Schelling Salon|739\n" +
			"Am Glockenbach 8, 80469 München, Deutschland, Schneewittchen|740\n" +
			"Odeonsplatz 7, 80539 München, Deutschland, Schumann's Bar am Hofgarten|741\n" +
			"Herzogstr., 80796 München, Deutschland, Schwabinger Wassermann|742\n" +
			"Alramstr., 81371 München, Deutschland, Sendlinger Augustiner|743\n" +
			"Türkenstr., 80799 München, Deutschland, Soda|744\n" +
			"St. Jakobsplatz 1, 80803 München, Deutschland, Stadtcafé|745\n" +
			"Gollierstr., 80339 München, Deutschland, Stoa|746\n" +
			"Dreimühlenstr., 80469 München, Deutschland, Tagträumer|747\n" +
			"Odeonsplatz 18, 80539 München, Deutschland, Tambosi|748\n" +
			"Frundsbergstr., 80634 München, Deutschland, The Big Easy|749\n" +
			"Frauenstr., 80469 München, Deutschland, The Victorian House|750\n" +
			"Türkenstr., 80799 München, Deutschland, The Victorian House|751\n" +
			"Theatinerstr., 80333 München, Deutschland, The Victorian House|752\n" +
			"Barer Str., 80799 München, Deutschland, The Victorian House|753\n" +
			"Ysenburgerstr., 80634 München, Deutschland, The Victorian House|754\n" +
			"Reichenbachstr., 80469 München, Deutschland, Trachtenvogl|755\n" +
			"Oertelplatz 1, 80999 München, Deutschland, Trattoria Olive|756\n" +
			"Leopoldstr., 80802 München, Deutschland, Vanilla Lounge|757\n" +
			"Brienner Str., 80333 München, Deutschland, Volksgarten|758\n" +
			"Türkenstr., 80799 München, Deutschland, Vorstadt Café|759\n" +
			"Fraunhoferstr., 80469 München, Deutschland, Wasserman Isarvorstadt|760\n" +
			"Elvirastr., 80636 München, Deutschland, Wassermann Neuhausen|761\n" +
			"Anglerstr., 80339 München, Deutschland, Westend|762\n" +
			"Neuturmstr., 80331 München, Deutschland, Wiener's|763\n" +
			"Ismaninger Str., 81675 München, Deutschland, Wiener's|765\n" +
			"Münchner Str., 82319 Starnberg, Deutschland, Wiener's - Der Kaffee-Röster|766\n" +
			"Terminal 2 Ebene 5, 85356 München, Deutschland, Wiener's - Der Kaffee-Röster|767\n" +
			"Rosenkavalierplatz 15, 81925 München, Deutschland, Wiener's Kaffebar|768\n" +
			"Elsässer Str., 81667 München, Deutschland, Wiesengrund|769\n" +
			"Lilienstr., 81669 München, Deutschland, Wirtshaus in der Au|770\n" +
			"Aberlestr., 81371 München, Deutschland, Wirtshaus Valleys|771\n" +
			"Lindwurmstr., 80337 München, Deutschland, Wu Wei|772\n" +
			"Wittelsbacher Str., 80469 München, Deutschland, Zoozie's|773\n" +
			"Flughafen München Terminal 2, 85356 München, Deutschland, Bagutta|774\n" +
			"Siegfriedstr., 80803 München, Deutschland, Bibulus Italienisches Restaurant|775\n" +
			"St.-Anna-Str., 80538 München, Deutschland, Cupido Restaurant Italienisch|776\n" +
			"Franz-Joseph-Str., 80801 München, Deutschland, Da Angelo Italienisches Restaurant|777\n" +
			"Bräuhausstr., 80331 München, Deutschland, Der Katzlmacher - Osteria|778\n" +
			"Zugspitzstr., 85591 Vaterstetten, Deutschland, Il Carretto Ristorante|779\n" +
			"Görresstr., 80798 München, Deutschland, Il Mulino - Italienisches|780\n" +
			"Schmalkaldener Str., 80807 München, Deutschland, L'angolo Restaurant München|781\n" +
			"Kölner Platz 7, 80804 München, Deutschland, La Piazza Trattoria|782\n" +
			"Nymphenburger Str., 80335 München, Deutschland, La Trattoria|783\n" +
			"Burgstr., 85604 Zorneding, Deutschland, Limone Trattoria, Bar, Festsaal|785\n" +
			"Fasaneriestr., 80636 München, Deutschland, Osteria Da Antonio|786\n" +
			"Margarethe-Danzi-Str., 80639 München, Deutschland, Primafila am Schlosspark|787\n" +
			"Maria-Eich.str., 82166 Gräfelfing, Deutschland, Ristorante Laurus - italienisches|788\n" +
			"Romanstr., 80639 München, Deutschland, Romans Ristorante|789\n" +
			"Westerholzstr., 81245 München, Deutschland, Speisemeisterei La Trattoria|790\n" +
			"Agnes-Bernauer-Str., 80687 München, Deutschland, Trattoria Lindengarten|791\n" +
			"Menzinger Str., 80997 München, Deutschland, Trattoria Menzinger`s|792\n" +
			"Frans-Hals-Str. 3, 81479 München, Deutschland, Al Pino - gehobene ital. Küche|793\n" +
			"Hochbrückenstr., 80331 München, BECCO FINO CUCINA E VINO|794\n" +
			"Holbeinstr., 81679 München, Deutschland, Casale Nuovo Restaurant|795\n" +
			"Baslerstr., 81476 München, Deutschland, Da Antonio Pasta + Lieferservice|796\n" +
			"Netzerstr., 80992 München, Deutschland, IL GAMBERO italienisch essen|797\n" +
			"Nymphenburger Str., 80335 München, Deutschland, La Bruschetta Trattoria|798\n" +
			"Maximilianstr., 80539 München, Deutschland, La Rocca Cucina Italia|799\n" +
			"Fraunhoferstr., 80469 München, Deutschland, Pasta e Basta der günstige|800\n" +
			"Zugspitzstr., 85551 Kirchheim - Heimstetten, Deutschland, Ristorantino Lucano|801\n" +
			"Kreillerstr., 81825 München, Deutschland, Trattoria Raphael München Ost|802\n" +
			"Thierschplatz 4, 80538 München, Deutschland, 12 Apostel Restaurant München|803\n" +
			"Bahnhofstr., 82069 Hohenschäftlarn, Deutschland, Il Brigante südlich v. München|804\n" +
			"Erdinger Str., 85609 Aschheim, Deutschland, Il Veliero Ristorante|805\n" +
			"Kaflerstr., 81241 München, Deutschland, Vapiano|806\n" +
			"Nymphenburger Str., 80639 München, Deutschland, Acetaia|807\n" +
			"Rotwandstr., 81539 München, Deutschland, Adesso|808\n" +
			"Leopoldstr., 80802 München, Deutschland, Adria Ristorante|809\n" +
			"Herzogstr., 80803 München, Deutschland, Ai Castelli|810\n" +
			"Hauptstr., 85259 Wiedenzhausen, Deutschland, Ai tre scalini|811\n" +
			"Dorfplatz 6, 85599 Parsdorf, Deutschland, Al Borgo|812\n" +
			"Diefenbachstr., 81479 München, Deutschland, Al Caminetto|813\n" +
			"Kreuzweg 4, 82131 Stockdorf, Deutschland, Al Castagno|814\n" +
			"Bahnhofstr., 82319 Starnberg, Deutschland, Al Gallo Nero|815\n" +
			"Prälat-Zistl-Str., 80331 München, Deutschland, Al Mercato|816\n" +
			"Leopoldstr., 80802 München, Deutschland, Al Pacino|817\n" +
			"Riemer Str., 81829 München, Deutschland, Al Vecchia|818\n" +
			"Sendlinger-Tor-Platz 5, 80336 München, Deutschland, Alla Scala|819\n" +
			"Heimeranplatz 1, 80339 München, Deutschland, Alpaladino|820";

	@Test
	public void run() {
		final String url = "https://www.google.com/search";
		final String[] lines = data.split("\n");
		final String token = "href=\"/url?q=http";
		for (String line : lines) {
			final String s[] = line.split("\\|");
			try {
				String html = IOUtils.toString(new URI(url + "?q=" + URLEncoder.encode(s[0], StandardCharsets.UTF_8.name())).toURL(),
						StandardCharsets.UTF_8);
				final Set<String> processed = new HashSet<>();
				while (html.contains(token)) {
					html = html.substring(html.indexOf(token) + token.length());
					final String u = "http" + html.substring(0, html.indexOf("&amp;")).toLowerCase();
					if (!processed.contains(u)) {
						processed.add(u);
						if (u.contains("://") && !u.contains("google") &&
								findEmail(IOUtils.toString(new URI(u).toURL(), StandardCharsets.UTF_8), u, s[1]))
							break;
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private boolean findEmail(String html, final String urlLocation, final String id) {
		html = html.toLowerCase().replace("[at]", "@").replace("(*at*)", "@");
		int pos = html.length();
		while ((pos = html.lastIndexOf('@', pos - 1)) > 0) {
			final Matcher matcher = emailPattern.matcher(
					html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length())));
			if (matcher.find()) {
				final String email = matcher.group(1);
				if (!email.endsWith(".png") && !email.endsWith(".jpg")
						&& (email.endsWith("@web.de") || email.endsWith("@gmx.de") || email.endsWith("@t-online.de")
								|| urlLocation.contains(email.substring(matcher.group(1).indexOf("@") + 1)) ||
								urlLocation.contains(email.substring(0, matcher.group(1).indexOf("@"))))) {
					System.out.println("update location set email='"
							+ email + "', url='" + urlLocation + "', modified_at=now() where id=" + id
							+ ";\n");
					return true;
				}
			}
		}
		return false;
	}
}
