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
	contact_eventParticipateCount,
	contact_list(true),
	contact_listBlocked,
	contact_listChat(true),
	contact_listChatFlat,
	contact_listEventParticipate(true),
	contact_listFriends,
	contact_listGroup,
	contact_listGroupLink,
	contact_listId,
	contact_listNotification(true),
	contact_listSupportCenter,
	contact_listVisit(true),
	contact_listWhat2do,
	contact_marketing(true),
	contact_maxAppVersion,
	contact_notification(true),
	contact_pingChat(true),
	contact_pingChatNew(true),
	contact_pingChatUnseen(true),
	contact_pingFriendRequest(true),
	contact_pingNotification(),
	contact_pingVisit(true),
	contact_token,
	contact_unique,
	contact_what2do,

	event_list(true),
	event_listCurrent(true),
	event_participate,

	location_list,
	location_listEvent(true),
	location_listEventCurrent(true),
	location_listFavorite,
	location_listOpenTime,
	location_listVisit,
	location_rating(true),
	location_ratingOverview(true),

	misc_geoLocation,
	misc_listLog,
	misc_listTicket,
	misc_setting;

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

	public String prepareSql(QueryParams params) {
		String search = Strings.isEmpty(params.getSearch()) ? "1=1" : sanatizeSearchToken(params.getSearch());
		if (params.getSearchGeoLocation() != null)
			search += " and " + params.getSearchGeoLocation();
		if (addBlock && params.getUser() != null)
			search += " and (select cb.id from ContactBlock cb where cb.contactId=contact.id and cb.contactId2={USERID} or cb.contactId={USERID} and cb.contactId2=contact.id) is null";
		String s = sql.replace("{search}", search);
		if (s.contains("{ID}"))
			s = s.replaceAll("\\{ID}", "" + params.getId());
		if (s.contains("{USERAGE}"))
			s = s.replaceAll("\\{USERAGE}",
					"" + (params.getUser().getAge() == null ? 0 : params.getUser().getAge()));
		if (s.contains("{USERGENDER}"))
			s = s.replaceAll("\\{USERGENDER}",
					"" + (params.getUser().getGender() == null ? 0 : params.getUser().getGender()));
		if (s.contains("{USERATTRIBUTES}"))
			s = s.replaceAll("\\{USERATTRIBUTES}",
					params.getUser().getAttr() == null
							|| params.getUser().getAttr().trim().length() == 0 ? "-"
									: params.getUser().getAttr().replace('\u0015', '|'));
		if (s.contains("{USERATTRIBUTESEX}"))
			s = s.replaceAll("\\{USERATTRIBUTESEX}",
					params.getUser().getAttrEx() == null || params.getUser().getAttrEx().trim().length() == 0 ? "-"
							: params.getUser().getAttrEx().replace(',', '|'));
		if (s.contains("{USERID}"))
			s = s.replaceAll("\\{USERID}", "" + params.getUser().getId());
		return s;
	}

	private String sanatizeSearchToken(String search) {
		if (search == null)
			return "";
		final StringBuilder s = new StringBuilder(search.toLowerCase());
		int p, p2;
		while ((p = s.indexOf("'")) > -1) {
			if ((p2 = s.indexOf("'", p + 1)) < 0)
				throw new IllegalArgumentException("Invalid search expression: " + sql);
			s.delete(p, p2 + 1);
		}
		if (s.indexOf(";") > -1 || s.indexOf("union") > -1 || s.indexOf("select") > -1 || s.indexOf("update") > -1
				|| s.indexOf("insert") > -1 || s.indexOf("delete") > -1)
			throw new IllegalArgumentException("Invalid search expression: " + sql);
		return search;

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
				cols.add(s[i].substring(0, s[i].lastIndexOf(',')));
			}
		}
		header = new String[cols.size()];
		cols.toArray(header);
	}

	public static Query get(String id) {
		return valueOf(Query.class, id.replace('/', '_'));
	}
}