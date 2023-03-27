package com.jq.findapp.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.jq.findapp.util.Strings;

public enum Query {
	contact_block,
	contact_chat,
	contact_list(true),
	contact_listBlocked,
	contact_listByLocation,
	contact_listChat(true),
	contact_listChatFlat,
	contact_listFriends,
	contact_listGeoLocationHistory,
	contact_listGroup,
	contact_listGroupLink,
	contact_listId,
	contact_listNews,
	contact_listNotification(true),
	contact_listSupportCenter,
	contact_listTeaser(true),
	contact_listVisit(true),
	contact_listVideoCalls,
	contact_maxAppVersion,
	contact_notification(true),
	contact_pingChat(true),
	contact_pingChatNew(true),
	contact_pingChatUnseen(true),
	contact_pingNotification,
	contact_token,

	event_list(true),
	event_listBlocked,
	event_listMatching(true),
	event_listParticipate(true),
	event_listParticipateRaw(true),
	event_listTeaser(true),

	location_list(true),
	location_listBlocked,
	location_listFavorite,
	location_listId,
	location_listVisit,

	misc_block,
	misc_geoLocation,
	misc_listIp,
	misc_listLog,
	misc_listTicket,
	misc_rating,
	misc_setting,
	misc_statsApi,
	misc_statsLocations,
	misc_statsLog,
	misc_statsUser;

	private final String sql;
	private final boolean addBlock;
	private String[] header;

	public static class Result {
		private final List<Object[]> list = new ArrayList<>();

		private Result(String[] header) {
			list.add(header);
		}

		public String[] getHeader() {
			return (String[]) list.get(0);
		}

		public List<Object[]> getList() {
			return list;
		}

		public int size() {
			return list.size() - 1;
		}

		public Map<String, Object> get(int index) {
			final Map<String, Object> one = new HashMap<>();
			for (int i = 0; i < list.get(0).length; i++)
				one.put(list.get(0)[i].toString(), list.get(index + 1)[i]);
			return one;
		}
	}

	public static String prepareSql(QueryParams params) {
		String search = Strings.isEmpty(params.getSearch()) ? "1=1" : sanatizeSearchToken(params);
		if (params.getSearchGeoLocation() != null)
			search += " and " + params.getSearchGeoLocation();
		if (params.getQuery().addBlock && params.getUser() != null) {
			if (params.getQuery().name().startsWith("contact_"))
				search += " and (select b.id from Block b where b.contactId=contact.id and b.contactId2={USERID} or b.contactId={USERID} and b.contactId2=contact.id) is null";
			else if (params.getQuery().name().startsWith("location_"))
				search += " and (select b.id from Block b where b.contactId={USERID} and b.locationId=location.id) is null";
			else if (params.getQuery().name().startsWith("event_"))
				search += " and (select b.id from Block b where b.contactId={USERID} and b.eventId=event.id) is null";
		}
		String s = params.getQuery().sql.replace("{search}", search);
		if (s.contains("{ID}"))
			s = s.replaceAll("\\{ID}", "" + params.getId());
		if (s.contains("{USERAGE}"))
			s = s.replaceAll("\\{USERAGE}",
					"" + (params.getUser().getAge() == null ? 0 : params.getUser().getAge()));
		if (s.contains("{USERGENDER}"))
			s = s.replaceAll("\\{USERGENDER}",
					"" + (params.getUser().getGender() == null ? 0 : params.getUser().getGender()));
		if (s.contains("{USERSKILLS}"))
			s = s.replaceAll("\\{USERSKILLS}",
					params.getUser().getSkills() == null
							|| params.getUser().getSkills().trim().length() == 0 ? "-"
									: params.getUser().getSkills());
		if (s.contains("{USERSKILLSTEXT}"))
			s = s.replaceAll("\\{USERSKILLSTEXT}",
					params.getUser().getSkillsText() == null || params.getUser().getSkillsText().trim().length() == 0
							? "-"
							: params.getUser().getSkillsText());
		if (s.contains("{USERID}"))
			s = s.replaceAll("\\{USERID}", "" + params.getUser().getId());
		return s;
	}

	private static String sanatizeSearchToken(QueryParams params) {
		if (params.getSearch() == null)
			return "";
		final StringBuilder s = new StringBuilder(params.getSearch().toLowerCase());
		int p, p2;
		while ((p = s.indexOf("'")) > -1) {
			p2 = p;
			do {
				p2 = s.indexOf("'", p2 + 1);
			} while (p2 > 0 && "\\".equals(s.substring(p2 - 1, p2)));
			if (p2 < 0)
				throw new IllegalArgumentException(
						"Invalid quote in " + params.getQuery().name() + " search: " + params.getSearch());
			s.delete(p, p2 + 1);
		}
		if (s.indexOf(";") > -1 || s.indexOf("union") > -1 || s.indexOf("select") > -1 || s.indexOf("update") > -1
				|| s.indexOf("insert") > -1 || s.indexOf("delete") > -1)
			throw new IllegalArgumentException(
					"Invalid expression in " + params.getQuery().name() + " search: " + params.getSearch());
		return params.getSearch();
	}

	public Result createResult() {
		return new Result(header);
	}

	private Query() {
		this(false);
	}

	private Query(boolean addBlock) {
		try {
			sql = IOUtils.toString(getClass().getResourceAsStream("/sql/" + name().replace("_", "/") + ".sql"),
					StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		if (addBlock && !sql.contains("{search}"))
			throw new RuntimeException("no {search} in " + name() + " for adding block statement");
		this.addBlock = addBlock;
		final String[] s = (sql.substring(sql.indexOf('\n') + 1, sql.indexOf("\nFROM\n")).trim() + ",").split("\n");
		final List<String> cols = new ArrayList<>();
		for (int i = 0; i < s.length; i++) {
			s[i] = s[i].trim();
			if (s[i].endsWith(",")) {
				if (s[i].contains(" as "))
					s[i] = '_' + s[i].substring(s[i].lastIndexOf(" as ") + 4).trim();
				cols.add(s[i].substring(0, s[i].lastIndexOf(',')).trim());
			}
		}
		header = new String[cols.size()];
		cols.toArray(header);
	}

	public static Query get(String id) {
		return valueOf(Query.class, id.replace('/', '_'));
	}

	public String getSql() {
		return sql;
	}
}