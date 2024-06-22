package com.jq.findapp.entity;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Entity;

@Entity
public class ClientMarketingResult extends BaseEntity {
	private BigInteger clientMarketingId;
	private String storage;
	private String image;
	private Boolean published;

	public static class PollResult {
		public int participants;
		public boolean finished;
		public final Map<String, Map<String, Object>> answers = new HashMap<>();
	}

	public BigInteger getClientMarketingId() {
		return clientMarketingId;
	}

	public void setClientMarketingId(final BigInteger clientMarketingId) {
		this.clientMarketingId = clientMarketingId;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public Boolean getPublished() {
		return published;
	}

	public void setPublished(final Boolean published) {
		this.published = published;
	}
}