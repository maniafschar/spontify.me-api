package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

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
import com.jq.findapp.util.Strings;

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
				save(ip);
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
		params.setQuery(Query.misc_listIp);
		for (int i = 0; i < result.size(); i++) {
			params.setSearch("ip.ip='" + result.get(i).get("log.ip") + "'");
			if (repository.list(params).size() == 0) {
				final String json = WebClient
						.create(lookupIp.replace("{ip}", (String) result.get(i).get("log.ip"))).get()
						.retrieve().toEntity(String.class).block().getBody();
				final Ip ip2 = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readValue(json, Ip.class);
				final String location = new ObjectMapper().readTree(json.getBytes(StandardCharsets.UTF_8)).get("loc")
						.asText();
				ip2.setLatitude(Float.parseFloat(location.split(",")[0]));
				ip2.setLongitude(Float.parseFloat(location.split(",")[1]));
				save(ip2);
			}
		}
	}

	private void save(Ip ip) throws Exception {
		try {
			repository.save(ip);
		} catch (Exception ex) {
			if (!Strings.stackTraceToString(ex).contains("Duplicate entry"))
				throw ex;
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setSearch("log.ip='" + ip.getIp() + "'");
			final Ip ip2 = repository.one(Ip.class, (BigInteger) repository.one(params).get("ip.id"));
			ip2.setCity(ip.getCity());
			ip2.setCountry(ip.getCountry());
			ip2.setHostname(ip.getHostname());
			ip2.setLatitude(ip.getLatitude());
			ip2.setLongitude(ip.getLongitude());
			ip2.setOrg(ip.getOrg());
			ip2.setPostal(ip.getPostal());
			ip2.setRegion(ip.getRegion());
			ip2.setTimezone(ip.getTimezone());
			repository.save(ip2);
		}
	}
}