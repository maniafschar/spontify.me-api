package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ApiTest {
	@LocalServerPort
	private int port;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Autowired
	private EngagementService engagementService;

	@Test
	public void test() throws Exception {
		// given
		String body = "{\"user\":{\"email\":\"ea95mHClVyMB5EwBMwpHUBVCZr9uEuUGqboGXoJgACsjLpJ8rw5XqOj+UdOhwk3Rfsf7i3t5tHIybIusMeCvzfwxkSOLl3Fr6Hh2bI9g+XeDzVy1B5HZDuzJiu40fengQ3hkPN6YLHMw8sFSfgE9hGUHnY1Nly12uvY5w0xvL/caeGxLCX8Ua8HY/dxvZf7Qd6ycp3s6yZN0klp2gJjkMkiNMTmFE3Ikm3BKaB5hVmNANddWvmaQHc/zj2RLSkb3T0KFRbIhuml/H50uRX6w9ZJ7R9DUX+1bFCxyldqY3F71U38Y9XAU9HdAaP42AVwGb5nFm5cRLaV33qs/Q6lng8gRKa1XZlI+yfU0/PgqkxD0fFCFiGMZYV18Aj00ZntbFn328W/uOcaYqsjMiS/ubu/Ax85YI4vE0uXCYVe1y9+CtNgMs0lSOTv9BxOh76Opf2eBgd1qpZwh6mm7WLo54c+MXq5fkbPHbJ15tWG0PSvi4CsREY0gray4Ua6nAmHegmZhVLj4tMe1WbYVOanHhjBJ7eWltku42GC3i8Jv0s361VHohO3o2eQbXFB5zNNual76bshmKA5otq9GTcS0Rd1tF2r0pQRDd5PLYU/K1y96AXX7LwKi/ddHBuE994UAsql+SWXS2kX2bcjL0JlnV2d5RBd79BPcfpsBgpgG5bo=\",\"authorizationCode\":\"xyz\",\"state\":\"\",\"identityToken\":\"xyz\",\"name\":\"Büşılay Ömäer\",\"id\":\"AMOm3mNt7GDoIdWT2/xI+KM/v4qW4Vd53yRW6lEr2FqFCEplOCHnL9saE8njBmP9sZ4zpAVgIxlThZ4KQuAwdE56LJZfJ/WTkI3tNU/0315FuofyYlrxdHtAPfxgD6bQyUJz765AOFhmf8cwZfPjKCFDZkInGB3cI9hsSWlVen9RKv90sU6M1eY3dAXjuOK7vwyKguCeo1l1OxTbXiTjBECE2DwERDRTVfRlvTCGAOK4V+XFlG16g1vUX1NqlSCmvJ9dVnyywOpAeVl21Z2nS8fP498GWYPCDEZbB/tMe5heQsiFyZL+PL0x+QglIZLhk0WIIzZnSslBHgN9Qd2DE7T1bNlTXNiEJqNCBM2VhBCG03Zq+lWIrzxwO28N9znN1tdKK28hnsUcQuE5mLKfaQFyIxMeepBr2byaE/c7M5q3tOirp6LsYtWWvGPik0B0Ns91/O9DNV3qjBYaKsO1GvhsNzE12Gmbk4XdyxGPTmfZXptC1ZWYblqbLRdiS3cXKCzfTR1lLOvkgAXsTNxB1KoXBBGIuL2NT1Lpk7l1yXcil/FD+74jsKZmQ324TPBcHXZHKquADVt2ksu9GcvAlIRGe8Ls+dGwXLKnSxRwINky05A5NtGjyP6kOBb5xPAtKc/KokUf932X9xvPk6XidmrfJitKIFn+sRhQAxL2yho=\"},\"from\":\"Apple\",\"language\":\"DE\",\"version\":\"0.9.4\",\"device\":\"phone\",\"os\":\"ios\",\"publicKey\":\"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCyQHZFhhEmQgXFECZgYaaiRo/ur5ZZf7K/nBd2yv6SQdXtPnXFteH1Uax7jOo/fgjkIjHTWYZ0np7H3eP6k4TEAYmTqAnNryzNQ/YBExrFuP9LQQEzGlEFj6vz4FLWhIISik6/eOWF+2oFT84720wWpvx1yx/NEhot0Cj5vcxn/wIDAQAB\"}";
		String path = "authentication/loginExternal";

		// when
		String response = WebClient.create("http://localhost:" + port + "/" + path)
				.put().contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toEntity(String.class).block()
				.getBody();

		// then
		assertNotNull(response);
		assertTrue(response.length() > 10);
	}

	@Test
	public void paypal() throws Exception {
		// given
		final JsonNode n = new ObjectMapper().readTree(
				"{\"scope\":\"https://uri.paypal.com/services/customer/partner-referrals/readwrite https://uri.paypal.com/services/invoicing https://uri.paypal.com/services/vault/payment-tokens/read https://uri.paypal.com/services/disputes/read-buyer https://uri.paypal.com/services/payments/realtimepayment https://uri.paypal.com/services/customer/onboarding/user https://api.paypal.com/v1/vault/credit-card https://api.paypal.com/v1/payments/.* https://uri.paypal.com/services/payments/referenced-payouts-items/readwrite https://uri.paypal.com/services/reporting/search/read https://uri.paypal.com/services/customer/partner https://uri.paypal.com/services/vault/payment-tokens/readwrite https://uri.paypal.com/services/customer/merchant-integrations/read https://uri.paypal.com/services/applications/webhooks https://uri.paypal.com/services/disputes/update-seller https://uri.paypal.com/services/payments/payment/authcapture openid Braintree:Vault https://uri.paypal.com/services/disputes/read-seller https://uri.paypal.com/services/payments/refund https://uri.paypal.com/services/risk/raas/transaction-context https://uri.paypal.com/services/partners/merchant-accounts/readwrite https://uri.paypal.com/services/identity/grantdelegation https://uri.paypal.com/services/customer/onboarding/account https://uri.paypal.com/payments/payouts https://uri.paypal.com/services/customer/onboarding/sessions https://api.paypal.com/v1/vault/credit-card/.* https://uri.paypal.com/services/subscriptions\",\"access_token\":\"A21AAIvYuE4je0EzM2m3pNgDQZl2EqeMtrLxleSuAkk7eOwSWiX4tJsesJYJLFW0en2NMthOuJ7kh3rKo9BU02hOFB9wNqp_Q\",\"token_type\":\"Bearer\",\"app_id\":\"APP-80W284485P519543T\",\"expires_in\":32159,\"nonce\":\"2023-01-12T14:05:38ZWBS2iWYRhqU6gi33dXD9yheH0wZOjfYJQMsipAyzAUE\"}");

		// when
		String accessToken = n.get("access_token").asText();

		// then
		assertEquals(
				"A21AAIvYuE4je0EzM2m3pNgDQZl2EqeMtrLxleSuAkk7eOwSWiX4tJsesJYJLFW0en2NMthOuJ7kh3rKo9BU02hOFB9wNqp_Q",
				accessToken);
	}

	// @Test // need to authorize Google API for IP
	public void createLocation() throws Exception {
		// given
		String body = "{\"classname\":\"Location\",\"values\":{\"name\":\"test\",\"address\":\"83700 Rottach-Egern\",\"category\":\"1\",\"latitude\":47.666,\"longitude\":11.777}}";
		String path = "db/one";
		final Contact contact = utils.createContact();

		// when
		String response = WebClient.create("http://localhost:" + port + "/" + path)
				.post().header("user", contact.getId().toString())
				.header("password", "79e38de3be6b328a857a02ef24ffa766f38b6c067d953032c70ea9caeaedaf4d")
				.header("salt", "1645254161315.7888940363091782")
				.contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toEntity(String.class)
				.block().getBody();

		// then
		assertNotNull(response);
		final Location location = repository.one(Location.class, new BigInteger(response));
		assertEquals(47.666f, location.getLatitude());
		assertEquals(11.777f, location.getLongitude());
		assertEquals("test", location.getName());
		assertEquals("Rottach-Egern", location.getTown());
		assertEquals("83700", location.getZipCode());
		assertEquals("DE", location.getCountry());
		assertEquals("Miesbach\nUpper Bavaria\nBavaria\nGermany", location.getAddress2());
	}
}