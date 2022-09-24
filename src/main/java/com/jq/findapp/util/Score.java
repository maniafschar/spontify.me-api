package com.jq.findapp.util;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Location;

public class Score {
	private static class Result {
		public double total = 0;
		public double match = 0;

		public double percantage() {
			return total == 0 ? 0 : match / total;
		}
	}

	public static String getSearchContact(Contact contact) throws Exception {
		String search = "";
		if (!Strings.isEmpty(contact.getBudget()))
			search += "REGEXP_LIKE(contact.budget, '" + contact.getBudget().replace('\u0015', '|') + "')=1) and (";
		if (!Strings.isEmpty(contact.getAgeMale()))
			search += "contact.gender=1 and contact.age>=" + contact.getAgeMale().split(",")[0] + " and contact.age<="
					+ contact.getAgeMale().split(",")[1] + " or ";
		if (!Strings.isEmpty(contact.getAgeFemale()))
			search += "contact.gender=2 and contact.age>=" + contact.getAgeFemale().split(",")[0] + " and contact.age<="
					+ contact.getAgeFemale().split(",")[1] + " or ";
		if (!Strings.isEmpty(contact.getAgeDivers()))
			search += "contact.gender=3 and contact.age>=" + contact.getAgeDivers().split(",")[0] + " and contact.age<="
					+ contact.getAgeDivers().split(",")[1] + " or ";
		if (search.contains("contact.age"))
			search = search.substring(0, search.length() - 4) + ") and (";
		for (int i = 0; i < 6; i++) {
			String attr = (String) contact.getClass().getMethod("getAttr" + i).invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(contact.attr" + i + ", '" + attr.replace('\u0015', '|') + "')=1 or ";
			attr = (String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(contact.attr" + i + "Ex, '" + attr.replace(',', '|') + "')=1 or ";
		}
		if (!Strings.isEmpty(contact.getAttrInterest()))
			search += "REGEXP_LIKE(contact.attr, '" + contact.getAttrInterest().replace('\u0015', '|') + "')=1 or ";
		if (!Strings.isEmpty(contact.getAttrInterestEx()))
			search += "REGEXP_LIKE(contact.attrEx, '" + contact.getAttrInterestEx().replace(',', '|') + "')=1 or ";
		if (search.endsWith(" or "))
			search = search.substring(0, search.length() - 4);
		if (search.endsWith("("))
			search += "1=1";
		return search;
	}

	private static void match(String attributes, String attributesCompare, Result result) {
		if (!Strings.isEmpty(attributes)) {
			final String[] attr = attributes.split(attributes.contains("\u0015") ? "\u0015" : ",");
			result.total += attr.length;
			if (attributesCompare != null) {
				for (int i2 = 0; i2 < attr.length; i2++) {
					if (attributesCompare.contains(attr[i2]))
						result.match++;
				}
			}
		}
	}

	public static double getContact(Contact contact, Contact contact2) throws Exception {
		final Result score = new Result();
		for (int i = 0; i < 6; i++) {
			match((String) contact.getClass().getMethod("getAttr" + i).invoke(contact),
					(String) contact2.getClass().getMethod("getAttr" + i).invoke(contact2), score);
			match((String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact),
					(String) contact2.getClass().getMethod("getAttr" + i + "Ex").invoke(contact2), score);
		}
		match(contact.getAttrInterest(), contact2.getAttr(), score);
		match(contact.getAttrInterestEx(), contact2.getAttrEx(), score);
		match(contact.getBudget(), contact2.getBudget(), score);
		return score.total < 8 ? 0 : score.percantage();
	}

	public static double getLocation(Contact contact, Location location) throws Exception {
		final Result score = new Result();
		match(location.getBudget(), contact.getBudget(), score);
		if (score.match > 0) {
			for (int i = 0; i < 6; i++) {
				match((String) location.getClass().getMethod("getAttr" + i).invoke(location),
						(String) contact.getClass().getMethod("getAttr" + i).invoke(contact), score);
				match((String) location.getClass().getMethod("getAttr" + i + "Ex").invoke(location),
						(String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact), score);
			}
		}
		return score.total < 2 ? 0 : score.percantage();
	}

}
