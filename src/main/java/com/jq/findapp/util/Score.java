package com.jq.findapp.util;

import com.jq.findapp.entity.Contact;

public class Score {
	private static class Result {
		public double total = 0;
		public double match = 0;

		public double percantage() {
			return total == 0 ? 0 : match / total;
		}
	}

	public static String getSearchContact(final Contact contact) throws Exception {
		String search = "";
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
		if (!Strings.isEmpty(contact.getSkills()))
			search += "cast(REGEXP_LIKE(contact.skills, '" + contact.getSkills() + "') as integer)=1 or ";
		if (!Strings.isEmpty(contact.getSkillsText()))
			search += "cast(REGEXP_LIKE(contact.skillsText, '" + contact.getSkillsText() + "') as integer)=1 or ";
		if (search.endsWith(" or "))
			search = search.substring(0, search.length() - 4);
		if (search.endsWith("("))
			search += "1=1";
		return search;
	}

	private static void match(final String attributes, final String attributesCompare, final Result result) {
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

	public static double getContact(final Contact contact, final Contact contact2) throws Exception {
		final Result score = new Result();
		match(contact.getSkills(), contact2.getSkills(), score);
		match(contact.getSkillsText(), contact2.getSkillsText(), score);
		return score.total < 8 ? 0 : score.percantage();
	}
}
