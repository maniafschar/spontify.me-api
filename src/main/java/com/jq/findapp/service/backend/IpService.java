package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class IpService {
	@Autowired
	private Repository repository;

	@Value("${app.url.lookupip}")
	private String lookupIp;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	public SchedulerResult lookupIps() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/lookupIps");
		final QueryParams params2 = new QueryParams(Query.misc_listIp);
		params2.setSearch("ip.longitude=0 and ip.latitude=0");
		final Result result2 = repository.list(params2);
		for (int i = 0; i < result2.size(); i++) {
			try {
				final Ip ip = repository.one(Ip.class, (BigInteger) result2.get(i).get("ip.id"));
				final String json = WebClient.create(lookupIp.replace("{ip}", ip.getIp())).get().retrieve()
						.toEntity(String.class).block().getBody();
				final Ip ip2 = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readValue(json, Ip.class);
				final String location = new ObjectMapper().readTree(json.getBytes(StandardCharsets.UTF_8)).get("loc")
						.asText();
				ip.setCity(ip2.getCity());
				ip.setCountry(ip2.getCountry());
				ip.setHostname(ip2.getHostname());
				ip.setOrg(ip2.getOrg());
				ip.setPostal(ip2.getPostal());
				ip.setRegion(ip2.getRegion());
				ip.setTimezone(ip2.getTimezone());
				ip.setLatitude(Float.parseFloat(location.split(",")[0]));
				ip.setLongitude(Float.parseFloat(location.split(",")[1]));
				repository.save(ip);
			} catch (Exception ex) {
				result.exception = ex;
			}
		}
		return result;
	}

	public void lookupLogIps() throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setLimit(0);
		params.setSearch("(log.uri like 'ad%' or log.uri like 'web%') and ip.org is null");
		final Result result = repository.list(params);
		final Set<Object> processed = new HashSet<>();
		for (int i = 0; i < result.size(); i++) {
			if (!processed.contains(result.get(i).get("log.ip"))) {
				processed.add(result.get(i).get("log.ip"));
				final String json = WebClient
						.create(lookupIp.replace("{ip}", (String) result.get(i).get("log.ip"))).get()
						.retrieve().toEntity(String.class).block().getBody();
				final Ip ip2 = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readValue(json, Ip.class);
				final String location = new ObjectMapper().readTree(json.getBytes(StandardCharsets.UTF_8)).get("loc")
						.asText();
				ip2.setLatitude(Float.parseFloat(location.split(",")[0]));
				ip2.setLongitude(Float.parseFloat(location.split(",")[1]));
				repository.save(ip2);
			}
		}
	}
}