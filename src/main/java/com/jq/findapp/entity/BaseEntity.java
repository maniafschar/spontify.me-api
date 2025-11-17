package com.jq.findapp.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Json;

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
		return this.id;
	}

	public void setId(final BigInteger id) {
		this.id = id;
	}

	public Timestamp getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(final Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public Timestamp getModifiedAt() {
		return this.modifiedAt;
	}

	public void setModifiedAt(final Timestamp modifiedAt) {
		this.modifiedAt = modifiedAt;
	}

	@Transient
	public boolean modified() {
		if (this.old == null)
			return true;
		for (final Field field : this.getClass().getDeclaredFields()) {
			try {
				if (this.old(field.getName()) != null)
					return true;
				if (this.old.get(field.getName()) == null) {
					final Field fieldActual = this.getClass().getDeclaredField(field.getName());
					fieldActual.setAccessible(true);
					if (fieldActual.get(this) != null)
						return true;
				}
			} catch (final Exception e) {
				throw new RuntimeException("Failed to check modified on " + field.getName(), e);
			}
		}
		return false;
	}

	@Transient
	public void historize() {
		if (this.id == null)
			return;
		if (this.old == null)
			this.old = new HashMap<>();
		for (final Field field : this.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				if (!this.old.containsKey(field.getName()))
					this.old.put(field.getName(), field.get(this));
			} catch (final Exception e) {
				throw new RuntimeException("Failed to historize on " + field.getName(), e);
			}
		}
	}

	@Transient
	public void populate(final Map<String, Object> values) {
		if (values != null && values.containsKey("image")) {
			try {
				this.getClass().getDeclaredMethod("getImageList");
				final String data = (String) values.get("image");
				final byte[] b = Entity.scaleImage(Base64.getDecoder().decode(
						data.substring(data.indexOf('\u0015') + 1)), Entity.IMAGE_THUMB_SIZE);
				values.put("imageList", Attachment.createImage(".jpg", b));
			} catch (final NoSuchMethodException e) {
				// entity does not have imageList, no need to add it
			}
		}
		final BaseEntity ref = Json.toObject(values, this.getClass());
		values.forEach((name, value) -> {
			if (!"id".equals(name) && !"createdAt".equals(name) && !"modifiedAt".equals(name)) {
				try {
					final Method m = this.getClass()
							.getDeclaredMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
					final Object valueOld = m.invoke(this), valueNew = m.invoke(ref);
					if (!Objects.equals(valueOld, valueNew)) {
						if (this.old != null)
							this.old.put(name, valueOld);
						if (value instanceof String && ((String) value).contains("<"))
							value = ((String) value).replace("<", "&lt;");
						this.getClass()
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
		if (this.old == null)
			return null;
		final Object value = this.old.get(name);
		if (value == null)
			return null;
		try {
			final Field field = this.getClass().getDeclaredField(name);
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
