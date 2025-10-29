package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Ai;
import com.jq.findapp.entity.Location;
import com.jq.findapp.util.Json;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class AiServiceTest {
	@Autowired
	private AiService aiService;

	@Test
	public void locations() {
		// given

		// when
		final List<Location> locations = this.aiService.locations(
				"I'm currenty in Munich, Germany, 80333 and I like Whisky and Beer. Plaese recommend me some locations.");

		// then
		assertNotNull(locations);
		assertTrue(locations.size() > 0);
	}

	@Test
	public void text() {
		// given

		// when
		final Ai ai = this.aiService.text(
				"Liste spannende Anekdoten zu den Begegnungen des FC Bayern München gegen 1. FC Nürnberg.");

		// then
		assertNotNull(ai);
		assertTrue(ai.getNote().length() > 2);
	}

	@Test
	public void result() {
		// given

		// when
		final JsonNode response = Json.toNode(
				"[{\"description\": \"Der FC Bayern München hat eine beeindruckende Bilanz gegen den 1. FC Nürnberg in den letzten 80 Jahren. Sie haben die meisten Begegnungen gewonnen, oft mit deutlichen Ergebnissen. Ein denkwürdiges Spiel war der 6:0-Sieg des FC Bayern in der Saison 1987/88, in dem Roland Wohlfarth einen Hattrick erzielte. Auch in jüngerer Zeit dominierte der FC Bayern, wie zum Beispiel der 3:0-Sieg in der Saison 2008/09, bei dem Franck Ribéry und Luca Toni trafen. Generell sind die Derbys zwischen diesen beiden bayerischen Vereinen oft von intensiven Zweikämpfen und leidenschaftlichen Fans geprägt, auch wenn die Ergebnisse in den letzten Jahrzehnten meist zugunsten des FC Bayern ausfielen.\",\"image\": \"https://upload.wikimedia.org/wikipedia/commons/thumb/c/c0/FC_Bayern_M%C3%BCnchen_vs_1._FC_N%C3%BCrnberg_2008.jpg/1280px-FC_Bayern_M%C3%BCnchen_vs_1._FC_N%C3%BCrnberg_2008.jpg\"}]");
		// "[
		// {
		// "description": "Die \"Franken-Derbys\" zwischen dem FC Bayern München und dem
		// 1. FC Nürnberg waren über Jahrzehnte hinweg von großer Rivalität und
		// spannenden Momenten geprägt. Ein frühes Highlight war der 7:0-Sieg des FC
		// Bayern in der Saison 1964/65, der die Dominanz des Rekordmeisters
		// unterstrich. In den 1970er Jahren lieferten sich die beiden Vereine oft
		// knappe Duelle, wobei der FC Bayern meist die Oberhand behielt. Ein
		// denkwürdiges Spiel fand 1985 statt, als der 1. FC Nürnberg dem FC Bayern ein
		// 1:1-Unentschieden abtrotzte und damit wichtige Punkte im Titelkampf holte.
		// Auch in den 2000er Jahren gab es packende Begegnungen, wie etwa das
		// 5:3-Spektakel in der Saison 2007/08, das auf beiden Seiten Chancen bot. Ein
		// besonderes Highlight war der 3:0-Auswärtssieg des 1. FC Nürnberg im Jahr
		// 2007, der für Aufsehen sorgte und zeigte, dass der \"Club\" durchaus in der
		// Lage war, den favorisierten Münchnern Paroli zu bieten. Die jüngere
		// Vergangenheit sah dagegen wieder klarere Bayern-Siege, wie das 5:1 im Jahr
		// 2018, das die spielerische Überlegenheit des FC Bayern demonstrierte.
		// Insgesamt spiegeln die Duelle die wechselnde Form und die unterschiedlichen
		// Erfolge beider Vereine wider, wobei jedes Spiel seine eigenen,
		// unvergesslichen Momente hatte.",
		// "image":
		// "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/FC_Bayern_M%C3%BCnchen_Logo_%282022%29.svg/1200px-FC_Bayern_M%C3%BCnchen_Logo_%282022%29.svg.png"
		// }
		// ]"

		// "Die Duelle zwischen dem FC Bayern München und dem 1. FC Nürnberg, oft als
		// "Franken-Derby" oder "Süd-Gipfel" bezeichnet, haben in den letzten 80 Jahren
		// eine reiche Geschichte mit zahlreichen Highlights und dramatischen Momenten
		// aufzuweisen.
		// Besonders in Erinnerung geblieben sind die **historischen
		// Meisterschaftsduelle**. In den Anfängen des deutschen Fußballs, als der Club
		// noch eine dominierende Kraft war, lieferten sich beide Vereine packende
		// Kämpfe um die deutsche Meisterschaft. Auch in der Bundesliga gab es immer
		// wieder entscheidende Spiele.
		// Ein herausragendes Highlight war zweifellos das **Pokalfinale 1985**, als der
		// 1. FC Nürnberg den favorisierten FC Bayern mit 3:2 besiegte und damit für
		// eine der größten Überraschungen im DFB-Pokal sorgte. Dieser Triumph ist bis
		// heute eine Ikone für die Nürnberger und eine schmerzhafte Erinnerung für die
		// Münchner.
		// In der jüngeren Vergangenheit, insbesondere seit dem Aufstieg des 1. FC
		// Nürnberg in die Bundesliga in den 2000er Jahren, waren die Begegnungen oft
		// von der **Dominanz des FC Bayern** geprägt. Dennoch gab es immer wieder
		// Spiele, in denen der Club dem Rekordmeister Paroli bieten konnte.
		// Beispielsweise sorgte Nürnberg mit kämpferisch starken Leistungen und
		// taktisch klugen Auftritten immer wieder für unerwartete Punktgewinne, die im
		// Abstiegskampf von entscheidender Bedeutung waren.
		// Auch individuelle Leistungen haben die Geschichte dieser Duelle geprägt.
		// Namen wie **Gerd Müller** auf Seiten der Bayern oder Legenden des 1. FC
		// Nürnberg haben sich in die Annalen dieser Begegnungen eingetragen. Tore, die
		// das Spiel entschieden, oder dramatische Wendungen, die die Fans auf den
		// Rängen mitrissen, sind fester Bestandteil der Erinnerung.
		// Obwohl der FC Bayern in den letzten Jahrzehnten deutlich erfolgreicher war,
		// behalten die Spiele gegen den 1. FC Nürnberg stets eine besondere Brisanz. Es
		// sind nicht nur drei Punkte, die auf dem Spiel stehen, sondern auch die Ehre
		// und die Tradition zweier bedeutender bayerischer Vereine."

		// "Der FC Bayern München und der 1. FC Nürnberg, zwei Traditionsvereine aus
		// Bayern, verbindet eine intensive und geschichtsträchtige Rivalität, die als
		// "Fränkisches Duell" bekannt ist. Die Begegnungen zwischen diesen beiden
		// Mannschaften haben oft für Dramatik, Emotionen und unvergessliche Momente
		// gesorgt.
		// Ein herausragendes Merkmal dieser Duelle ist die Dominanz des FC Bayern, der
		// historisch gesehen die Oberhand behielt. Dennoch hat der 1. FC Nürnberg immer
		// wieder bewiesen, dass er in der Lage ist, dem Favoriten ein Bein zu stellen
		// und für Überraschungen zu sorgen. Diese Spiele waren selten von Taktik
		// geprägt, sondern eher von Kampf, Leidenschaft und einem hohen Tempo.
		// Besonders denkwürdig sind die Spiele, in denen Nürnberg den Bayern lange Zeit
		// ein Bein stellen konnte oder sogar als Sieger vom Platz ging. Diese "kleinen"
		// Erfolge gegen den großen Nachbarn wurden in Nürnberg frenetisch gefeiert und
		// haben sich tief in die Fankultur eingebrannt. Umgekehrt sind die deutlichen
		// Siege des FC Bayern, oft geprägt von individueller Klasse und spielerischer
		// Überlegenheit, Teil der eigenen Erfolgsgeschichte.
		// Einige Spiele ragten durch besondere Wendungen oder herausragende
		// Einzelleistungen heraus. Die Atmosphäre bei diesen Begegnungen war stets
		// aufgeladen, sowohl im Münchner Stadion als auch in Nürnberg, wo die
		// Heimspiele oft zu Hexenkesseln wurden. Die Rivalität sorgte dafür, dass sich
		// Spieler und Fans über die Grenzen des Sports hinaus emotional engagierten.
		// Auch wenn der 1. FC Nürnberg in den letzten Jahrzehnten eher in der 2.
		// Bundesliga zu finden war, wodurch die Häufigkeit der Aufeinandertreffen
		// abnahm, bleiben die Erinnerungen an diese brisanten Duelle lebendig. Das
		// "Fränkische Duell" steht exemplarisch für die Faszination des deutschen
		// Fußballs mit seinen tiefen regionalen Wurzeln und der Leidenschaft, die
		// solche Nachbarschaftsduelle entfachen können. Es ist eine Geschichte von
		// David gegen Goliath, von unerwarteten Siegen und von der unerbittlichen
		// Rivalität zweier bayerischer Fußballgrößen."

		// "**Der ewige Rivale: Bayerns Highlights gegen den 1. FC Nürnberg**
		// Die Duelle zwischen dem FC Bayern München und dem 1. FC Nürnberg, oft als
		// "Fränkische" oder "Bayerische Meisterschaft" bezeichnet, gehören zu den
		// traditionsreichsten und emotionalsten Begegnungen im deutschen Fußball.
		// Obwohl die Dominanz des FC Bayern in den letzten Jahrzehnten unbestritten
		// ist, gab es auch immer wieder denkwürdige Momente, in denen der Club dem
		// Rekordmeister Paroli bot oder sogar Erfolge feierte.
		// Ein frühes Highlight war zweifellos der **5:0-Sieg des 1. FC Nürnberg im
		// Viertelfinale des DFB-Pokals 1984**, der die Bayern unerwartet aus dem
		// Wettbewerb warf und für eine Sensation sorgte. Dieses Ergebnis hallte lange
		// nach und gilt bis heute als einer der größten Erfolge des Clubs gegen die
		// Münchner.
		// In der Bundesliga gab es ebenfalls packende Duelle. Der **1. FC Nürnberg
		// konnte den FC Bayern im Jahr 2007 mit 3:0 im Frankenstadion besiegen**, eine
		// Leistung, die in Nürnberg wie ein Titel gefeiert wurde. Torschützen wie Jan
		// Koller und Veton Shala sorgten für unvergessliche Momente.
		// Auch wenn der FC Bayern meist die Oberhand behielt, gab es immer wieder
		// Momente, in denen sich die Spieler des 1. FC Nürnberg in die Geschichtsbücher
		// eintrugen. Der **legendäre Siegtreffer von Robert Vittek in der Saison
		// 2007/08**, der den Bayern eine Heimniederlage zufügte, ist vielen Fans noch
		// in Erinnerung.
		// In jüngerer Vergangenheit waren die Begegnungen oft von der Überlegenheit der
		// Bayern geprägt, dennoch versuchten die Nürnberger immer wieder, den
		// Rekordmeister zu ärgern. Tore von Spielern wie **Marco Terrazzino oder Eduard
		// Löwen** in den letzten Aufeinandertreffen zeugen davon, dass der Kampfgeist
		// des Clubs nie gebrochen war, auch wenn die Punkte oft in München blieben.
		// Die Highlights dieser Spiele sind nicht nur die Tore oder die Ergebnisse,
		// sondern auch die Leidenschaft, die in diesen Partien auf dem Platz lag. Sie
		// symbolisieren die tiefe Verwurzelung des Fußballs in Bayern und die ewige
		// Rivalität zwischen Tradition und Erfolg."

		// "Gerne, hier sind einige spannende Anekdoten aus den zahlreichen Begegnungen
		// zwischen dem FC Bayern München und dem 1. FC Nürnberg, oft als "Fränkisches
		// Derby" oder "Derby gegen den Rivalen" bezeichnet:
		// **Die historische Dominanz des FC Bayern und die Ausnahmen:**
		// * **Das Jahr 1963: Der legendäre 1:0-Sieg des Club:** Obwohl der FC Bayern
		// heute eine überragende Bilanz gegen Nürnberg hat, gibt es immer wieder
		// Ausnahmen, die sich ins Gedächtnis brennen. Der vielleicht berühmteste Sieg
		// des Club gegen die Bayern datiert aus dem Jahr 1963, als Nürnberg in der
		// Oberliga Süd eine starke Mannschaft stellte. Der 1:0-Sieg war nicht nur ein
		// wichtiger Erfolg im Kampf um die Meisterschaft, sondern auch ein emotionaler
		// Triumph über den aufstrebenden Rivalen aus München. Dieses Spiel wird oft als
		// Beispiel dafür genannt, dass im Derby alles möglich ist, auch wenn die
		// Papierform dagegen spricht.
		// * **Die "Geisterspiele" in den 70ern und 80ern:** In den goldenen Jahren des
		// FC Bayern mit Beckenbauer, Müller und Maier waren die Partien gegen Nürnberg
		// oft von einer gewissen Einseitigkeit geprägt. Doch selbst in diesen Phasen
		// gab es immer wieder Nürnberger, die sich in den Vordergrund spielten und den
		// Bayern das Leben schwer machten. Die Anekdoten erzählen von Nürnberger
		// Verteidigern, die den Bayern-Stürmern ihren Stempel aufdrückten, oder von
		// Torhütern, die über sich hinauswuchsen und den Bayern-Angriff verzweifeln
		// ließen.
		// **Emotionen und Dramatik:**
		// * **Der umstrittene Elfmeter von 2007:** Ein Spiel, das vielen Nürnberger
		// Fans bis heute bitter aufstößt, fand im April 2007 statt. Der FC Bayern
		// gewann mit 1:0 durch einen Elfmeter in der Nachspielzeit. Die Kontroverse
		// entstand, weil der Elfmeter nach einem Foul von Nürnbergs Andreas Wolf an
		// Bayerns Lukas Podolski gegeben wurde. Die Nürnberger waren überzeugt, dass
		// der Ball vor dem Kontakt im Aus war oder der Kontakt nicht ausreichend war.
		// Dieser Treffer hat nicht nur die damalige Tabelle beeinflusst, sondern auch
		// die Rivalität weiter angeheizt und für lange Diskussionen gesorgt.
		// * **Das "Müller-Duell" aus den 70ern:** Auch wenn es oft Gerd Müller war, der
		// die Bayern-Tore schoss, gab es in den Nürnberger Reihen ebenfalls Spieler,
		// die diese Rolle ausfüllten. Die Anekdoten beschreiben oft die Duelle zwischen
		// dem "Bomber der Nation" und Nürnberger Verteidigern, die versuchten, ihn aus
		// dem Spiel zu nehmen. Manchmal gelang es ihnen, was für Nürnberger
		// Verhältnisse schon ein Teilerfolg war.
		// **Die besondere Atmosphäre des Derbys:**
		// * **Die Fangesänge und die Rivalität:** Unabhängig von den Ergebnissen war
		// und ist das Derby immer von einer besonderen Atmosphäre geprägt. Die
		// Fangesänge beider Lager, die gegenseitigen Provokationen und die Leidenschaft
		// auf den Rängen sind Teil der Geschichte. Anekdoten berichten von Fans, die
		// Wochen vor dem Spiel auf die Partie hinfieberten, von besonderen
		// Choreografien und von einem immensen Druck, den die Fans auf die Spieler
		// ausüben.
		// * **Die "kleine" Stadt gegen die "große" Stadt:** Oft wurde und wird das
		// Derby auch als Aufeinandertreffen der bodenständigen Franken gegen die
		// weltläufigeren Bayern gesehen. Diese Klischees speisen die Rivalität und
		// sorgen für zusätzliche Brisanz. Anekdoten erzählen davon, wie die Nürnberger
		// ihre Herkunft und ihren Kampfgeist betonen, während die Bayern ihre Dominanz
		// und ihren Erfolg ins Feld führen.
		// **Die "underdog"-Momente des Clubs:**
		// * **Die Überraschungssiege:** Obwohl der FC Bayern in den letzten Jahrzehnten
		// meist die Oberhand behielt, gab es immer wieder Momente, in denen der 1. FC
		// Nürnberg für eine echte Sensation sorgte. Diese Spiele, oft durch taktische
		// Meisterleistungen, unbändigen Kampfgeist und eine Portion Glück, sind für die
		// Nürnberger Fans von unschätzbarem Wert und werden in der Vereinsgeschichte
		// verewigt. Sie zeigen, dass im Derby der Außenseiter durchaus für eine
		// Überraschung sorgen kann.
		// * **Das Tor des Monats/Jahres:** Auch wenn es nicht direkt eine Begegnung
		// ist, so sind doch herausragende Einzelaktionen, die im Derby stattfanden,
		// Teil der Anekdoten. Ein spektakuläres Tor, das vielleicht sogar zum Tor des
		// Monats oder Jahres gewählt wurde, kann einem Nürnberger Spieler unsterblichen
		// Ruhm verleihen und die Erinnerung an ein bestimmtes Derby für immer prägen.
		// Diese Anekdoten sind oft eine Mischung aus sportlicher Rivalität, lokaler
		// Identität, emotionalen Höhen und Tiefen und dem unvorhersehbaren Charakter
		// des Fußballs. Sie machen das Fränkische Derby zu einem der
		// traditionsreichsten und mit Spannung erwarteten Spiele im deutschen Fußball,
		// auch wenn die jüngere Geschichte oft zugunsten des FC Bayern ausfiel."

		// then
		assertNotNull(response);
		assertNotNull(response.get(0).get("description"));
		assertNotNull(response.get(0).get("image"));
	}
}
