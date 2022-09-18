package com.jq.findapp.repository;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBlock;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.entity.LocationRating;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.service.WhatToDoService;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Component
public class RepositoryListener {
	private static Repository repository;
	private static NotificationService notificationService;
	private static ExternalService externalService;
	private static WhatToDoService whatToDoService;
	private static BigInteger adminId;

	@Value("${app.admin.id}")
	private void setAdminId(BigInteger adminId) {
		RepositoryListener.adminId = adminId;
	}

	@Autowired
	private void setRepository(Repository repository) {
		RepositoryListener.repository = repository;
	}

	@Autowired
	private void setNotificationService(NotificationService notificationService) {
		RepositoryListener.notificationService = notificationService;
	}

	@Autowired
	private void setExternalService(ExternalService externalService) {
		RepositoryListener.externalService = externalService;
	}

	@Autowired
	private void setWhatToDoService(WhatToDoService whatToDoService) {
		RepositoryListener.whatToDoService = whatToDoService;
	}

	@javax.persistence.PrePersist
	public void prePersist(final BaseEntity entity) throws Exception {
		if (entity instanceof Contact)
			PrePersist.contact((Contact) entity);
		else if (entity instanceof ContactBluetooth)
			PrePersist.contactBluetooth((ContactBluetooth) entity);
		else if (entity instanceof Feedback)
			PrePersist.feedback((Feedback) entity);
		else if (entity instanceof Location)
			PrePersist.location((Location) entity);
		else if (entity instanceof LocationFavorite)
			PrePersist.locationFavorite((LocationFavorite) entity);
	}

	@javax.persistence.PreUpdate
	public void preUpdate(final BaseEntity entity) throws Exception {
		if (entity instanceof Contact)
			PreUpdate.contact((Contact) entity);
		else if (entity instanceof Location)
			PreUpdate.location((Location) entity);
	}

	@javax.persistence.PostPersist
	public void postPersist(final BaseEntity entity) throws Exception {
		if (entity instanceof Chat)
			PostPersist.chat((Chat) entity);
		else if (entity instanceof ContactBlock)
			PostPersist.contactBlock((ContactBlock) entity);
		else if (entity instanceof ContactBluetooth)
			PostPersist.contactBluetooth((ContactBluetooth) entity);
		else if (entity instanceof ContactLink)
			PostPersist.contactLink((ContactLink) entity);
		else if (entity instanceof ContactVisit)
			PostPersist.contactVisit((ContactVisit) entity);
		else if (entity instanceof ContactWhatToDo)
			PostPersist.contactWhatToDo((ContactWhatToDo) entity);
		else if (entity instanceof EventParticipate)
			PostPersist.eventParticipate((EventParticipate) entity);
		else if (entity instanceof Feedback)
			PostPersist.feedback((Feedback) entity);
		else if (entity instanceof Location)
			PostPersist.location((Location) entity);
		else if (entity instanceof LocationRating)
			PostPersist.locationRating((LocationRating) entity);
		else if (entity instanceof LocationVisit)
			PostPersist.locationVisit((LocationVisit) entity);
	}

	@javax.persistence.PostUpdate
	public void postUpdate(final BaseEntity entity) throws Exception {
		if (entity instanceof ContactLink)
			PostUpdate.contactLink((ContactLink) entity);
		else if (entity instanceof ContactVisit)
			PostUpdate.contactVisit((ContactVisit) entity);
		else if (entity instanceof ContactWhatToDo)
			PostUpdate.contactWhatToDo((ContactWhatToDo) entity);
	}

	private static class PrePersist {
		private static void contact(final Contact contact) {
			contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
		}

		private static void contactBluetooth(final ContactBluetooth contactBlutooth) {
			final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
			contactBlutooth.setLatitude(me.getLatitude());
			contactBlutooth.setLongitude(me.getLongitude());
		}

		private static void feedback(final Feedback feedback) {
			if (feedback.getLocalized() != null && feedback.getLocalized().length() > 50)
				feedback.setLocalized(feedback.getLocalized().substring(0, 50));
			if (feedback.getText() != null && feedback.getText().length() > 2000)
				feedback.setText(feedback.getText().substring(0, 2000));
			if (feedback.getResponse() != null && feedback.getResponse().length() > 2000)
				feedback.setResponse(feedback.getResponse().substring(0, 2000));
		}

		private static void locationFavorite(LocationFavorite locationFavorite)
				throws JsonMappingException, JsonProcessingException, IllegalAccessException {
			locationFavorite.setFavorite(Boolean.TRUE);
		}

		private static void location(Location location)
				throws JsonMappingException, JsonProcessingException, IllegalAccessException {
			lookupAddress(location);
			final QueryParams params = new QueryParams(Query.location_list);
			location.getCategory();
			location.getName();
			params.setUser(repository.one(Contact.class, location.getContactId()));
			params.setSearch(
					"REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(location.address),'''',''),'\\n',''),'\\r',''),'\\t',''),' ','')='"
							+ location.getAddress().toLowerCase().replaceAll("'", "").replaceAll("\n", "")
									.replaceAll("\r", "").replaceAll("\t", "").replaceAll(" ", "")
							+ "'");
			final Result list = repository.list(params);
			for (int i = 0; i < list.size(); i++) {
				if (isNameMatch((String) list.get(i).get("location.name"), location.getName(), true))
					throw new IllegalAccessException("Location exists");
			}
		}
	}

	private static boolean isNameMatch(String name1, String name2, boolean tryReverse) {
		name1 = name1.trim().toLowerCase();
		name2 = name2.trim().toLowerCase();
		while (name1.contains("  "))
			name1 = name1.replaceAll("  ", " ");
		final String[] n = name1.split(" ");
		int count = 0;
		for (int i = 0; i < n.length; i++) {
			if (name2.contains(n[i]))
				count++;
		}
		if (count == n.length)
			return true;
		if (tryReverse)
			return isNameMatch(name2, name1, false);
		return false;
	}

	private static class PreUpdate {
		private static void location(Location location) throws Exception {
			if (location.old("address") != null)
				lookupAddress(location);
		}

		private static void contact(final Contact contact) throws Exception {
			if (contact.old("visitPage") != null)
				contact.setVisitPage(new Timestamp(System.currentTimeMillis()));
			if (contact.old("pushToken") != null)
				repository.executeUpdate(
						"update Contact contact set contact.pushToken=null, contact.pushSystem=null where contact.pushToken='"
								+ contact.old("pushToken") + "' and contact.id<>" + contact.getId());
			if (contact.old("fbToken") != null)
				repository.executeUpdate(
						"update Contact contact set contact.fbToken=null where contact.fbToken='"
								+ contact.old("fbToken")
								+ "' and contact.id<>" + contact.getId());
			if (contact.old("pseudonym") != null)
				contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
			if (contact.getBirthday() == null)
				contact.setAge(null);
			else {
				final GregorianCalendar now = new GregorianCalendar();
				final GregorianCalendar birthday = new GregorianCalendar();
				birthday.setTimeInMillis(contact.getBirthday().getTime());
				short age = (short) (now.get(Calendar.YEAR) - birthday.get(Calendar.YEAR));
				if (now.get(Calendar.MONTH) < birthday.get(Calendar.MONTH) ||
						now.get(Calendar.MONTH) == birthday.get(Calendar.MONTH) &&
								now.get(Calendar.DAY_OF_MONTH) < birthday.get(Calendar.DAY_OF_MONTH))
					age--;
				contact.setAge(age);
			}
			if (contact.getVerified()) {
				final QueryParams params = new QueryParams(Query.contact_chat);
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId());
				if (repository.list(params).size() == 0) {
					final Chat chat = new Chat();
					chat.setContactId(adminId);
					chat.setContactId2(contact.getId());
					chat.setSeen(false);
					chat.setTextId(Text.mail_welcome.name());
					chat.setNote(
							MessageFormat.format(Text.mail_welcome.getText(contact.getLanguage()),
									contact.getPseudonym()));
					repository.save(chat);
				}
			}
		}
	}

	private static class PostPersist {
		private static void feedback(final Feedback feedback) throws Exception {
			final Chat chat = new Chat();
			chat.setContactId(feedback.getContactId());
			chat.setContactId2(adminId);
			chat.setNote(feedback.getText());
			chat.setSeen(Boolean.TRUE);
			repository.save(chat);
			notificationService.sendNotification(repository.one(Contact.class, adminId),
					repository.one(Contact.class, feedback.getContactId()),
					NotificationID.feedback, null, "FEEDBACK " + feedback.getText());
		}

		private static void contactBluetooth(final ContactBluetooth contactBlutooth)
				throws Exception {
			final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
			final Contact other = repository.one(Contact.class, contactBlutooth.getContactId2());
			if (me.getFindMe() != null && me.getFindMe() && other.getFindMe() != null && other.getFindMe()) {
				notificationService.sendNotificationOnMatch(NotificationID.findMe, me, other);
				notificationService.sendNotificationOnMatch(NotificationID.findMe, other, me);
			}
		}

		private static void contactBlock(final ContactBlock contactBlock)
				throws Exception {
			notificationService.sendEmail(null, "BLOCK " + contactBlock.getContactId2(),
					"id: " + contactBlock.getId() +
							"\ncontactId: " + contactBlock.getContactId() +
							"\ncontactId2: " + contactBlock.getContactId2() +
							"\nreason: " + contactBlock.getReason() +
							"\nnote: " + contactBlock.getNote());
		}

		private static void chat(final Chat chat) throws Exception {
			final Contact contactFrom = repository.one(Contact.class, chat.getContactId());
			if (chat.getLocationId() == null) {
				final Contact contactTo = repository.one(Contact.class, chat.getContactId2());
				String s = null;
				if (chat.getNote() == null)
					s = Text.mail_sentImg.getText(contactTo.getLanguage());
				else {
					s = chat.getNote();
					if (s.indexOf(Attachment.SEPARATOR) > -1)
						s = new String(Repository.Attachment.getFile(s), StandardCharsets.UTF_8);
					if (s.indexOf(" :openPos(") == 0)
						s = (contactFrom.getGender() == null || contactFrom.getGender() == 2 ? Text.mail_sentPos2
								: Text.mail_sentPos1)
								.getText(contactTo.getLanguage());
					else if (s.indexOf(" :open(") == 0)
						s = (s.lastIndexOf(" :open(") == 0 ? Text.mail_sentEntry : Text.mail_sentEntries)
								.getText(contactTo.getLanguage());
				}
				notificationService.sendNotification(contactFrom, contactTo, NotificationID.newMsg,
						"chat=" + contactFrom.getId(), s);
			} else
				notificationService.locationNotifyOnMatch(contactFrom, chat.getLocationId(),
						NotificationID.chatLocation, chat.getNote());
		}

		private static void contactLink(ContactLink contactLink) throws Exception {
			notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId()),
					repository.one(Contact.class, contactLink.getContactId2()),
					NotificationID.friendReq, Strings.encodeParam("p=" + contactLink.getContactId()));
		}

		private static void contactVisit(ContactVisit contactVisit) throws Exception {
			notificationService.sendNotificationOnMatch(NotificationID.visitProfile, repository.one(Contact.class,
					contactVisit.getContactId()), repository.one(Contact.class, contactVisit.getContactId2()));
		}

		private static void contactWhatToDo(ContactWhatToDo contactWhatToDo) throws Exception {
			whatToDoService.findAndNotify();
		}

		private static void eventParticipate(EventParticipate eventParticipate) throws Exception {
			final Event event = repository.one(Event.class, eventParticipate.getEventId());
			if (event != null && !event.getContactId().equals(eventParticipate.getContactId())) {
				final Contact contactTo = repository.one(Contact.class, event.getContactId());
				final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
				notificationService.sendNotification(contactFrom, contactTo,
						NotificationID.markEvent,
						Strings.encodeParam("p=" + contactFrom.getId()),
						new SimpleDateFormat("dd.MM.yyyy HH:mm").format(eventParticipate.getEventDate()),
						event.getText(),
						repository.one(Location.class, event.getLocationId()).getName());
			}
		}

		private static void location(Location location) throws Exception {
			final LocationFavorite locationFavorite = new LocationFavorite();
			locationFavorite.setContactId(location.getContactId());
			locationFavorite.setLocationId(location.getId());
			locationFavorite.setFavorite(true);
			repository.save(locationFavorite);
		}

		private static void locationRating(LocationRating locationRating) throws Exception {
			repository.executeUpdate(
					"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id) where location.id="
							+ locationRating.getLocationId());
			notificationService.locationNotifyOnMatch(
					repository.one(Contact.class, locationRating.getContactId()),
					locationRating.getLocationId(), NotificationID.ratingLocMat,
					repository.one(Location.class, locationRating.getLocationId()).getName());
		}

		private static void locationVisit(LocationVisit locationVisit) throws Exception {
			notificationService.locationNotifyOnMatch(repository.one(Contact.class, locationVisit.getContactId()),
					locationVisit.getLocationId(), NotificationID.visitLocation, null);
		}
	}

	private static class PostUpdate {
		private static void contactLink(ContactLink contactLink) throws Exception {
			if (contactLink.getStatus() == Status.Friends) {
				notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId2()),
						repository.one(Contact.class, contactLink.getContactId()),
						NotificationID.friendAppro, Strings.encodeParam("p=" + contactLink.getContactId2()));
				final QueryParams params = new QueryParams(Query.contact_marketing);
				params.setUser(repository.one(Contact.class, contactLink.getContactId()));
				params.setSearch(
						"contactMarketing.data='" + contactLink.getContactId2() + "' and contactMarketing.type='"
								+ ContactMarketing.Type.CollectFriends.name() + "' and contactMarketing.contactId="
								+ contactLink.getContactId());
				if (repository.list(params).size() == 0) {
					final ContactMarketing marketing = new ContactMarketing();
					marketing.setContactId(contactLink.getContactId());
					marketing.setData(contactLink.getContactId2().toString());
					marketing.setType(ContactMarketing.Type.CollectFriends);
					repository.save(marketing);
				}
			}
		}

		private static void contactVisit(ContactVisit contactVisit) throws Exception {
			notificationService.sendNotificationOnMatch(NotificationID.visitProfile, repository.one(Contact.class,
					contactVisit.getContactId()), repository.one(Contact.class, contactVisit.getContactId2()));
		}

		private static void contactWhatToDo(ContactWhatToDo contactWhatToDo) throws Exception {
			whatToDoService.findAndNotify();
		}
	}

	private static void lookupAddress(Location location)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		final JsonNode googleAddress = new ObjectMapper().readTree(
				externalService.google("geocode/json?address="
						+ location.getName() + ", " + location.getAddress().replaceAll("\n", ", ")));
		if (!"OK".equals(googleAddress.get("status").asText()))
			throw new IllegalAccessException("Invalid address:\n"
					+ new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(googleAddress));
		final JsonNode result = googleAddress.get("results").get(0);
		JsonNode n = result.get("geometry").get("location");
		final GeoLocation geoLocation = externalService.convertGoogleAddress(googleAddress);
		location.setAddress(geoLocation.getFormatted());
		location.setCountry(geoLocation.getCountry());
		location.setTown(geoLocation.getTown());
		location.setZipCode(geoLocation.getZipCode());
		location.setStreet(geoLocation.getStreet());
		location.setNumber(geoLocation.getNumber());
		if (geoLocation.getStreet() != null && geoLocation.getStreet().trim().length() > 0) {
			location.setLatitude(geoLocation.getLatitude());
			location.setLongitude(geoLocation.getLongitude());
		}
		n = result.get("address_components");
		String s = "";
		for (int i = 0; i < n.size(); i++) {
			if (!location.getAddress().contains(n.get(i).get("long_name").asText()))
				s += '\n' + n.get(i).get("long_name").asText();
		}
		location.setAddress2(s.trim());
	}

	private static String sanitizePseudonym(String pseudonym) {
		pseudonym = pseudonym.trim().replaceAll("[^a-zA-ZÀ-ÿ0-9-_.+*#§$%&/\\\\ \\^']", "");
		int i = 0;
		while (pseudonym.length() < 9)
			pseudonym += (char) ('a' + i++);
		return pseudonym;
	}
}