package com.jq.findapp.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;

@MappedSuperclass
public abstract class BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private BigInteger id;

	private Timestamp createdAt;
	private Timestamp modifiedAt;

	@Transient
	private Map<String, Object> old = null;

	public BigInteger getId() {
		return id;
	}

	public void setId(final BigInteger id) {
		this.id = id;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(final Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public Timestamp getModifiedAt() {
		return modifiedAt;
	}

	public void setModifiedAt(final Timestamp modifiedAt) {
		this.modifiedAt = modifiedAt;
	}

	@Transient
	public boolean modified() {
		if (old == null)
			return true;
		for (final Field field : getClass().getDeclaredFields()) {
			try {
				if (old(field.getName()) != null)
					return true;
				final Field fieldActual = getClass().getDeclaredField(field.getName());
				fieldActual.setAccessible(true);
				if (fieldActual.get(this) != null)
					return true;
			} catch (final Exception e) {
				throw new RuntimeException("Failed to check modified on " + field.getName(), e);
			}
		}
		return false;
	}

	@Transient
	public void historize() {
		if (id == null)
			return;
		if (old == null)
			old = new HashMap<>();
		for (final Field field : getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				if (!old.containsKey(field.getName()))
					old.put(field.getName(), field.get(this));
			} catch (final Exception e) {
				throw new RuntimeException("Failed to historize on " + field.getName(), e);
			}
		}
	}

	@Transient
	public void populate(final Map<String, Object> values) {
		final BaseEntity ref = new ObjectMapper().convertValue(values, this.getClass());
		values.forEach((name, value) -> {
			if (!"id".equals(name) && !"createdAt".equals(name) && !"modifiedAt".equals(name)) {
				try {
					final Method m = getClass()
							.getDeclaredMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
					final Object valueOld = m.invoke(this), valueNew = m.invoke(ref);
					if (!Objects.equals(valueOld, valueNew)) {
						if (old != null)
							old.put(name, valueOld);
						if (value instanceof String && ((String) value).contains("<"))
							value = ((String) value).replace("<", "&lt;");
						getClass()
								.getDeclaredMethod("set" + name.substring(0, 1).toUpperCase() + name.substring(1),
										m.getReturnType())
								.invoke(this, valueNew);
					}
				} catch (final Exception ex) {
					throw new RuntimeException("Failed on " + name + ", value " + value, ex);
				}
			}
		});
	}

	@Transient
	public Object old(final String name) {
		if (old == null)
			return null;
		final Object value = old.get(name);
		if (value == null)
			return null;
		try {
			final Field field = getClass().getDeclaredField(name);
			field.setAccessible(true);
			final Object compare = field.get(this);
			return value.equals(compare)
					|| value instanceof String && Attachment.resolve((String) value).equals(compare) ? null : value;
		} catch (final Exception ex) {
			throw new RuntimeException("Failed to read " + name, ex);
		}
	}

	@Transient
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return false;
	}
}
