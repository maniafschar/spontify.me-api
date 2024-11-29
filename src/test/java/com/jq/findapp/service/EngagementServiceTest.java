package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EngagementServiceTest {
	@Autowired
	private EngagementService engagementService;

	@Autowired
	private Utils utils;

	@Test
	public void sendChats() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);

		// when
		engagementService.cron();

		// then no exception
	}

	@Test
	public void readVersion() {
		// given
		final String js = ",a+1),10));if(i==parseInt(e.substring(e.lastIndexOf(\"=\")+1),10))return n}}},{key:\"enco\",value:function(t){if(!t)return t;var e,n,i,a,o,r=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=\",u=0,s=0,l=\"\",c=[];do{e=(o=t.charCodeAt(u++)<<16|t.charCodeAt(u++)<<8|t.charCodeAt(u++))>>18&63,n=o>>12&63,i=o>>6&63,a=63&o,c[s++]=r.charAt(e)+r.charAt(n)+r.charAt(i)+r.charAt(a)}while(u<t.length);l=c.join(\"\");var _=t.length%3;return(_?l.slice(0,_-3):l)+\"===\".slice(_||3)}},{key:\"encParam\",value:function(e){for(var n=0,i=2;i<e.length;i++)isNaN(e.substring(i,i+1))||(n+=parseInt(e.substring(i,i+1),10));return t.enco(e)+\"=\"+n}},{key:\"getDevice\",value:function(){var t=navigator.userAgent.toLowerCase(),e=t.indexOf(\"mobile\")>-1,n=t.indexOf(\"android\")>-1,i=t.indexOf(\"phone\")>-1,a=t.indexOf(\"ipad\")>-1;return n&&!e||a?\"tablet\":e||n||i?\"phone\":\"computer\"}},{key:\"getOS\",value:function(){return t.isBrowser()?\"web\":/Android/i.test(navigator.userAgent)||/amazon-fireos/i.test(navigator.userAgent)?\"android\":\"ios\"}},{key:\"getParam\",value:function(e){var n;if(window.location&&window.location.search&&(n=window.location.search),(!n||n.indexOf(\"?\")<0)&&(n=t.url),n){if(n.indexOf(\"?\")>-1&&(n=n.substring(n.indexOf(\"?\")+1)),!e)return n;n=n.split(\"&\");for(var i=0;i<n.length;i++){var a=n[i].split(\"=\");if(a[0]==e)return decodeURI(a[1])}}}},{key:\"getRegEx\",value:function(t,e){return e?\"REGEXP_LIKE(\"+t+\",'\"+e.replace(e.indexOf(\",\")>-1?/,/g:/\u0015/g,\"|\")+\"')=1\":\"1=0\"}},{key:\"isBrowser\",value:function(){return!window.cordova}},{key:\"template\",value:function(t){for(var e=t[0],n=1;n<t.length;n++)(arguments[n]||0==arguments[n])&&(e+=arguments[n]),e+=t[n];return e}}],null&&u(e.prototype,null),n&&u(e,n),Object.defineProperty(e,\"prototype\",{writable:!1}),t}();s(l,\"appTitle\",\"skillvents\"),s(l,\"appVersion\",\"0.1.8\"),s(l,\"language\",null),s(l,\"minLocations\",5),s(l,\"paused\",!1),s(l,\"server\",\"https://skills.community/rest/\"),s(l,\"serverImg\",\"\"),s(l,\"separator\",\" Â· \"),s(l,\"url\",\"\"),s(l,\"date\",{formatDate:function(t,e){if(!t)return\"\";var n=l.date.server2Local(t);return n instanceof Date?r.ui.l(\"weekday\"+(e?\"Long\":\"\")+n.getDay())+\" \"+n.getDate()+\".\"+(n.getMonth()+1)+\".\"+(n.getFullYear()+\" \").slice(-3)+n.getHours()+\":\"+(\"0\"+n.getMinutes()).slice(-2):n},getDateFields:function(t){if(t instanceof Date)return{year:t.getFullYear(),month:(\"0\"+(t.getMonth()+1)).slice(-2),day:(\"0\"+t.getDate()).slice(-2),hour:(\"0\"+t.getHours()).slice(-2),minute:(\"0\"+t.getMinutes()).slice(-2),second:(\"0\"+t.getSeconds()).slice(-2),time:!0};if(t.year&&t.day)return t;t.indexOf(\"-\")<0&&8==t.length&&(t=t.substring(0,4)+\"-\"+t.substring(4,6)+\"-\"+t.substring(6));var e=t.indexOf(\"-\"),n=t.indexOf(\"-\",e+1),i=t.replace(\"T\",\" \").indexOf(\" \"),a=t.indexOf(\":\"),o=t.indexOf(\":\",a+1),r=t.indexOf(\".\");return{year:t.substring(0,e),month:t.substring(e+1,n),day:t.substring(n+1,i<0?t.length:i),hour:a<0?0:t.substring(i+1,a),minute:a<0?0:t.substring(a+1,o>0?o:t.length)";
		final Matcher matcher = Pattern.compile("\"appVersion\"([^\\)]+)").matcher(js);

		// when
		matcher.find();
		final String match = matcher.group(1).replaceAll("[^0-9.]", "");

		// then
		assertEquals("0.1.8", match);
	}

	@Test
	public void timeToSendNewRegistrationReminder_first() {
		timeToSendNewRegistrationReminder(6, -1, true);
	}

	@Test
	public void timeToSendNewRegistrationReminder_second_false() {
		timeToSendNewRegistrationReminder(6, 1, false);
	}

	@Test
	public void timeToSendNewRegistrationReminder_second_true() {
		timeToSendNewRegistrationReminder(7, 1, true);
	}

	@Test
	public void timeToSendNewRegistrationReminder_third_false() {
		timeToSendNewRegistrationReminder(15, 7, false);
	}

	@Test
	public void timeToSendNewRegistrationReminder_third_false2() {
		timeToSendNewRegistrationReminder(34, 7, false);
	}

	@Test
	public void timeToSendNewRegistrationReminder_third_true() {
		timeToSendNewRegistrationReminder(35, 7, true);
	}

	private void timeToSendNewRegistrationReminder(int createdBeforeDays, int lastReminderDays, boolean expected) {
		// given
		final Contact contact = new Contact();
		contact.setCreatedAt(new Timestamp(Instant.now().minus(Duration.ofDays(createdBeforeDays)).toEpochMilli()));

		// when
		final boolean send = engagementService.timeToSendNewRegistrationReminder(
				lastReminderDays < 0 ? null
						: new Timestamp(Instant.ofEpochMilli(contact.getCreatedAt().getTime())
								.plus(Duration.ofDays(lastReminderDays)).plus(Duration.ofHours(1)).toEpochMilli()),
				contact);

		// then
		assertEquals(expected, send);
	}
}