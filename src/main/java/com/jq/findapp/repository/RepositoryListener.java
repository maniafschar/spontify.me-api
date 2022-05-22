package com.jq.findapp.repository;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactRating;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationRating;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.ExternalService.Address;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RepositoryListener {
	private static Repository repository;
	private static NotificationService notificationService;
	private static ExternalService externalService;
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

	@PrePersist
	public void prePersist(final BaseEntity entity) throws Exception {
		if (entity instanceof Contact)
			prePersistContact((Contact) entity);
		else if (entity instanceof ContactBluetooth)
			prePersistContactBluetooth((ContactBluetooth) entity);
		else if (entity instanceof Location)
			prePersistLocation((Location) entity);
		else if (entity instanceof Feedback)
			prePersistFeedback((Feedback) entity);
	}

	private void prePersistContact(final Contact contact) {
		contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
	}

	@PreUpdate
	public void preUpdate(final BaseEntity entity) throws Exception {
		if (entity instanceof Contact)
			preUpdateContact((Contact) entity);
		else if (entity instanceof Location)
			preUpdateLocation((Location) entity);
	}

	@PostPersist
	public void postPersist(final BaseEntity entity) throws Exception {
		if (entity instanceof Feedback)
			postPersistFeedback((Feedback) entity);
		else if (entity instanceof ContactBluetooth)
			postPersistContactBluetooth((ContactBluetooth) entity);
		else if (entity instanceof Chat)
			postPersistChat((Chat) entity);
		else if (entity instanceof ContactLink)
			postPersistContactLink((ContactLink) entity);
		else if (entity instanceof EventParticipate)
			postPersistEventParticipate((EventParticipate) entity);
		else if (entity instanceof ContactRating)
			postPersistContactRating((ContactRating) entity);
		else if (entity instanceof LocationRating)
			postPersistLocationRating((LocationRating) entity);
	}

	@PostUpdate
	public void postUpdate(final BaseEntity entity) throws Exception {
		if (entity instanceof ContactLink)
			postUpdateContactLink((ContactLink) entity);
	}

	private void prePersistContactBluetooth(final ContactBluetooth contactBlutooth) {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		contactBlutooth.setLatitude(me.getLatitude());
		contactBlutooth.setLongitude(me.getLongitude());
	}

	private void prePersistFeedback(final Feedback feedback) {
		if (feedback.getLocalized() != null && feedback.getLocalized().length() > 50)
			feedback.setLocalized(feedback.getLocalized().substring(0, 50));
		if (feedback.getText() != null && feedback.getText().length() > 2000)
			feedback.setText(feedback.getText().substring(0, 2000));
		if (feedback.getResponse() != null && feedback.getResponse().length() > 2000)
			feedback.setResponse(feedback.getResponse().substring(0, 2000));
	}

	private void prePersistLocation(Location location)
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
		if (repository.list(params).size() > 0)
			throw new IllegalAccessException("Location exists");

	}

	private void lookupAddress(Location location)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		final JsonNode googleAddress = new ObjectMapper().readTree(
				externalService.google("geocode/json?address=" + location.getAddress().replaceAll("\n", ", ")));
		if (!"OK".equals(googleAddress.get("status").asText()))
			throw new IllegalAccessException("Invalid address");
		final JsonNode result = googleAddress.get("results").get(0);
		JsonNode n = result.get("geometry").get("location");
		location.setLatitude(n.get("lat").floatValue());
		location.setLongitude(n.get("lng").floatValue());
		final Address address = externalService.convertGoogleAddress(googleAddress);
		location.setAddress(address.getFormatted());
		location.setCountry(address.country);
		location.setTown(address.town);
		location.setZipCode(address.zipCode);
		location.setStreet(address.street);
		n = result.get("address_components");
		String s = "";
		for (int i = 0; i < n.size(); i++) {
			if (!location.getAddress().contains(n.get(i).get("long_name").asText()))
				s += '\n' + n.get(i).get("long_name").asText();
		}
		location.setAddress2(s.trim());
	}

	private void preUpdateLocation(Location location) throws Exception {
		if (location.old("address") == null) {
			location.setLatitude((Float) location.old("latitude"));
			location.setLongitude((Float) location.old("longitude"));
		} else
			lookupAddress(location);
	}

	private String sanitizePseudonym(String pseudonym) {
		pseudonym = pseudonym.replaceAll("[^a-zA-ZÀ-ÿ0-9-_.+*#§$%&/(){}\\[\\]\\^=?! \\\\]", "");
		int i = 0;
		while (pseudonym.length() < 8)
			pseudonym += ++i;
		return pseudonym;
	}

	private void preUpdateContact(final Contact contact) throws Exception {
		if (contact.old("visitPage") != null)
			contact.setVisitPage(new Timestamp(System.currentTimeMillis()));
		if (contact.old("pushToken") != null)
			repository.executeUpdate(
					"update Contact contact set contact.pushToken=null, contact.pushSystem=null where contact.pushToken='"
							+ contact.old("pushToken") + "' and contact.id<>" + contact.getId());
		if (contact.old("fbToken") != null)
			repository.executeUpdate(
					"update Contact contact set contact.fbToken=null where contact.fbToken='" + contact.old("fbToken")
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
			if (birthday.get(Calendar.MONTH) < now.get(Calendar.MONTH) ||
					birthday.get(Calendar.DAY_OF_MONTH) < now.get(Calendar.DAY_OF_MONTH))
				age--;
			contact.setAge(age);
		}
	}

	private void postPersistFeedback(final Feedback feedback) throws Exception {
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

	private void postPersistContactBluetooth(final ContactBluetooth contactBlutooth)
			throws Exception {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		final Contact other = repository.one(Contact.class, contactBlutooth.getContactId2());
		if (me.getFindMe() && other.getFindMe()) {
			notificationService.sendNotificationOnMatch(NotificationID.findMe, me, other);
			notificationService.sendNotificationOnMatch(NotificationID.findMe, other, me);
		}
	}

	private void postPersistChat(final Chat chat) throws Exception {
		final Contact contactFrom = repository.one(Contact.class, chat.getContactId());
		if (chat.getLocationId() == null) {
			final Contact contactTo = repository.one(Contact.class, chat.getContactId2());
			String s = null;
			if (chat.getNote() == null)
				s = Text.mail_sentImg.getText(contactTo.getLanguage());
			else {
				s = chat.getNote();
				if (s.indexOf(" :openPos(") == 0)
					s = (contactFrom.getGender() == 2 ? Text.mail_sentPos2 : Text.mail_sentPos1)
							.getText(contactTo.getLanguage());
				else if (s.indexOf(" :open(") == 0)
					s = (s.lastIndexOf(" :open(") == 0 ? Text.mail_sentEntry : Text.mail_sentEntries)
							.getText(contactTo.getLanguage());
			}
			notificationService.sendNotification(contactFrom, contactTo, NotificationID.newMsg,
					"chat=" + contactFrom.getId(), s);
		} else
			notificationService.locationNotifyOnMatch(contactFrom, chat.getLocationId(), NotificationID.chatLocation,
					chat.getNote());
	}

	private void postUpdateContactLink(ContactLink contactLink) throws Exception {
		if (contactLink.getStatus() == Status.Friends) {
			notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId2()),
					repository.one(Contact.class, contactLink.getContactId()),
					NotificationID.friendAppro, Strings.encodeParam("p=" + contactLink.getContactId2()));
			final QueryParams params = new QueryParams(Query.contact_marketing);
			params.setUser(repository.one(Contact.class, contactLink.getContactId()));
			params.setSearch("contactMarketing.data='" + contactLink.getContactId2() + "' and contactMarketing.type='"
					+ ContactMarketing.Type.CollectFriends.name() + "' and contactMarketing.contactId="
					+ contactLink.getContactId());
			if (repository.list(params).size() == 0) {
				final ContactMarketing marketing = new ContactMarketing();
				marketing.setContactId(contactLink.getContactId());
				marketing.setData(contactLink.getContactId2().toString());
				marketing.setType(com.jq.findapp.entity.ContactMarketing.Type.CollectFriends);
				repository.save(marketing);
			}
		}
	}

	private void postPersistContactLink(ContactLink contactLink) throws Exception {
		notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId()),
				repository.one(Contact.class, contactLink.getContactId2()),
				NotificationID.friendReq, Strings.encodeParam("p=" + contactLink.getContactId()));
	}

	private void postPersistEventParticipate(EventParticipate eventParticipate) throws Exception {
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

	private void postPersistContactRating(ContactRating contactRating) throws Exception {
		repository.executeUpdate(
				"update Contact contact set rating=(select sum(rating)/count(*) from ContactRating where contactId2=contact.id) where contact.id="
						+ contactRating.getContactId2());
		notificationService.sendNotification(
				repository.one(Contact.class, contactRating.getContactId()),
				repository.one(Contact.class, contactRating.getContactId2()),
				NotificationID.ratingProfile, Strings.encodeParam("p=" + contactRating.getContactId()),
				contactRating.getRating() + "%");
	}

	private void postPersistLocationRating(LocationRating locationRating) throws Exception {
		repository.executeUpdate(
				"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id) where location.id="
						+ locationRating.getLocationId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationRating.getContactId()),
				locationRating.getLocationId(), NotificationID.ratingLocMat,
				repository.one(Location.class, locationRating.getLocationId()).getName());
	}
}