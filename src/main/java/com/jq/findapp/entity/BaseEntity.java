package com.jq.findapp.entity;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.repository.Repository;

@MappedSuperclass
public class BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private BigInteger id;

	private Timestamp createdAt;
	private Timestamp modifiedAt;

	@Transient
	private transient Map<String, Object> old;

	public BigInteger getId() {
		return id;
	}

	public void setId(BigInteger id) {
		this.id = id;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public Timestamp getModifiedAt() {
		return modifiedAt;
	}

	public void setModifiedAt(Timestamp modifiedAt) {
		this.modifiedAt = modifiedAt;
	}

	@Transient
	public void populate(Map<String, Object> values) {
		final BaseEntity ref = new ObjectMapper().convertValue(values, this.getClass());
		values.forEach((name, value) -> {
			if (!"id".equals(name) && !"createdAt".equals(name) && !"modifiedAt".equals(name)) {
				Object v = null;
				try {
					final Method m = getClass()
							.getDeclaredMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
					v = m.invoke(ref);
					if (old == null)
						old = new HashMap<>();
					old.put(name, m.invoke(this));
					getClass()
							.getDeclaredMethod("set" + name.substring(0, 1).toUpperCase() + name.substring(1),
									m.getReturnType())
							.invoke(this, v);
				} catch (Exception ex) {
					throw new RuntimeException("Failed on " + name + ", value " + v, ex);
				}
			}
		});
	}

	@Transient
	public Object old(String name) {
		return old == null ? null : old.get(name);
	}

	@Transient
	public boolean writeAccess(BigInteger user, Repository repository) {
		return false;
	}
}