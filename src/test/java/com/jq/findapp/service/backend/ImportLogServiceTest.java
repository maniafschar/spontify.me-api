package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Log;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ImportLogServiceTest {
	@Autowired
	private ImportLogService importLogService;

	@Test
	public void mapCall_queryEventListParticipateRaw() throws Exception {
		// given
		final Log log = createLog("GET", "/db/list",
				"query=event_listParticipateRaw&search=eventParticipate.contactId=3", null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("pageEvent.loadEvents(params)", call);
	}

	@Test
	public void mapCall_bodyContact() throws Exception {
		// given
		final Log log = createLog("PUT", "/db/one", null,
				"{\"values\":{\"active\":false},\"classname\":\"Contact\",\"id\":3}");

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("initialisation.initApp()", call);
	}

	@Test
	public void mapCall_bodyContactPush() throws Exception {
		// given
		final Log log = createLog("PUT", "/db/one", null,
				"{\"values\":{\"pushSystem\":\"ios\",\"pushToken\":\"abc\"},\"classname\":\"Contact\",\"id\":814}");

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("communication.notification.saveToken(e)", call);
	}

	@Test
	public void mapCall_bodyContactStorage() throws Exception {
		// given
		final Log log = createLog("PUT", "/db/one", null,
				"{\"values\":{\"storage\":\"{\"location\":{\"values\":{\"description\":\"\",\"address\":\"\",\"latitude\":\"48.13684\",\"longitude\":\"11.57685\",\"town\":\"\",\"street\":\"\",\"zipCode\":\"\",\"country\":\"\",\"address2\":\"\",\"name\":\"\",\"telephone\":\"\"}");

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("user.remove(key)", call);
	}

	@Test
	public void mapCall_queryEventList() throws Exception {
		// given
		final Log log = createLog("GET", "/db/list",
				"query=event_list&distance=100000&latitude=48.0723044&longitude=11.5239053&search=event.contactId=3",
				null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("initialisation.initApp()", call);
	}

	@Test
	public void mapCall_queryPaypal() throws Exception {
		// given
		final Log log = createLog("GET", "/action/paypalKey", null, null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("", call);
	}

	@Test
	public void mapCall_queryGoogle() throws Exception {
		// given
		final Log log = createLog("GET", "/action/google", "param=js", null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("communication.loadMap(callback)", call);
	}

	@Test
	public void mapCall_queryMap() throws Exception {
		// given
		final Log log = createLog("GET", "/action/map",
				"source=48.07236401320413,11.52387354880961&destination=48.072323,11.524", null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("pageLocation.detailLocationEvent(l, id)", call);
	}

	@Test
	public void mapCall_queryQuotation() throws Exception {
		// given
		final Log log = createLog("GET", "/action/quotation", null, null);

		// when
		final String call = importLogService.mapCall(log);

		// then
		assertEquals("pageChat.insertQuote(event)", call);
	}

	private Log createLog(String method, String uri, String query, String body) {
		final Log log = new Log();
		log.setMethod(method);
		log.setUri(uri);
		log.setQuery(query);
		log.setBody(body);
		log.setCreatedAt(new Timestamp(System.currentTimeMillis()));
		return log;
	}
}