package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Strings;

import jakarta.transaction.Transactional;

@RestController
@Transactional
@CrossOrigin(origins = { Strings.URL_APP, Strings.URL_APP_NEW, Strings.URL_LOCALHOST, Strings.URL_LOCALHOST_TEST })
@RequestMapping("statistics")
public class StatisticsApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@GetMapping("contact")
	public List<Object[]> contact(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		if (contact.getType() == ContactType.adminContent) {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			return repository.list(params).getList();
		}
		return null;
	}
}