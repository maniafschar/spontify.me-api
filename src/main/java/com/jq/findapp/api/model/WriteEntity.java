package com.jq.findapp.api.model;

import java.math.BigInteger;
import java.util.Map;

import com.jq.findapp.entity.BaseEntity;

public class WriteEntity {
	private BigInteger id;
	private String classname;
	private Map<String, Object> values;

	public BigInteger getId() {
		return id;
	}

	public void setId(BigInteger id) {
		this.id = id;
	}

	public Class<BaseEntity> getClazz() {
		try {
			return (Class<BaseEntity>) Class.forName(BaseEntity.class.getPackage().getName() + "." + classname);
		} catch (ClassNotFoundException ex) {
			throw new RuntimeException(ex);
		}
	}

	public String getClassname() {
		return classname;
	}

	public void setClassname(String classname) {
		this.classname = classname;
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> values) {
		this.values = values;
	}

	@Override
	public String toString() {
		return classname + (id == null ? "" : ": " + id) + (values == null ? "" : " " + values.keySet());
	}
}