package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;

import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("statistics")
public class StatisticsApi {
	@Autowired
	private Repository repository;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Autowired
	private AuthenticationService authenticationService;

	@GetMapping("contact/{query}")
	public List<Object[]> contact(@PathVariable final String query, @RequestHeader final BigInteger user)
			throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		if (contact.getType() == ContactType.adminContent) {
			final QueryParams params = new QueryParams(Query.valueOf("misc_stats" + query));
			params.setLimit(0);
			params.setUser(contact);
			return repository.list(params).getList();
		}
		return null;
	}

	@GetMapping("contact/location")
	public List<Object[]> contactLocation(@RequestHeader final BigInteger user) throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		if (contact.getType() == ContactType.adminContent) {
			final QueryParams params = new QueryParams(Query.contact_statisticsGeoLocation);
			params.setSearch("contact.longitude is not null and contact.id<>" + adminId);
			// + " and contactGeoLocationHistory.manual=false and contact.clientId=" +
			// contact.getClientId());
			final Result result = repository.list(params);
			final List<Object[]> latLng = new ArrayList<>();
			latLng.add(new Object[] { "contact.latitude", "contact.longitude" });
			for (int i = 0; i < result.size(); i++) {
				latLng.add(new Float[] { (Float) result.get(i).get("geoLocation.latitude"),
						(Float) result.get(i).get("geoLocation.longitude") });
			}
			return latLng;
		}
		return null;
	}

	@GetMapping("marketing")
	public List<Object[]> marketing(@RequestHeader final BigInteger user) throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		if (contact.getType() == ContactType.adminContent) {
			final QueryParams params = new QueryParams(Query.misc_listMarketing);
			params.setSearch("clientId=" + contact.getClientId());
			return repository.list(params).getList();
		}
		return null;
	}
}