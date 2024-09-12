package com.jq.findapp.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

public enum Query {
	contact_block,
	contact_chat,
	contact_list(true),
	contact_listBlocked,
	contact_listChat(true),
	contact_listChatFlat,
	contact_listFriends,
	contact_listGeoLocationHistory,
	contact_listGroup,
	contact_listGroupLink,
	contact_listId,
	contact_listMarketing,
	contact_listNotification(true),
	contact_listNotificationId(),
	contact_listReferer,
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
	contact_statisticsGeoLocation,
	contact_token,

	event_list(true),
	event_listId(),
	event_listBlocked,
	event_listMatching(true),
	event_listParticipate(true),
	event_listParticipateRaw,
	event_listTeaser(true),
	event_listTeaserMeta,
	event_rating,

	location_list(true),
	location_listBlocked,
	location_listFavorite,
	location_listId,
	location_listVisit,

	misc_block,
	misc_geoLocation,
	misc_listClient,
	misc_listGeoLocation,
	misc_listIp,
	misc_listLog,
	misc_listMarketing,
	misc_listMarketingResult,
	misc_listNews,
	misc_listStorage,
	misc_listTicket,
	misc_setting,
	misc_statsApi,
	misc_statsLocations,
	misc_statsLog,
	misc_statsUser;

	private final String sql;
	private final String[] header;
	final boolean addBlock;

	public static class Result {
		private final List<Object[]> list = new ArrayList<>();

		private Result(final String[] header) {
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

		public Map<String, Object> get(final int index) {
			final Map<String, Object> one = new HashMap<>();
			for (int i = 0; i < list.get(0).length; i++)
				one.put(list.get(0)[i].toString(), list.get(index + 1)[i]);
			return one;
		}

		public void forEach(Consumer<Map<String, Object>> action) {
			for (int i = 0; i < size(); i++)
				action.accept(get(i));
		}
	}

	public String[] getHeader() {
		return header;
	}

	public Result createResult() {
		return new Result(header);
	}

	private Query() {
		this(false);
	}

	private Query(final boolean addBlock) {
		try (final InputStream in = getClass().getResourceAsStream("/sql/" + name().replace("_", "/") + ".sql")) {
			sql = IOUtils.toString(in, StandardCharsets.UTF_8);
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

	public static Query get(final String id) {
		return valueOf(Query.class, id.replace('/', '_'));
	}

	public String getSql() {
		return sql;
	}
}
