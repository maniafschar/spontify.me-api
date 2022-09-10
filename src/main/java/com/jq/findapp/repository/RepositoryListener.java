package com.jq.findapp.repository;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

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
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.entity.LocationRating;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

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
		else if (entity instanceof Feedback)
			prePersistFeedback((Feedback) entity);
		else if (entity instanceof Location)
			prePersistLocation((Location) entity);
		else if (entity instanceof LocationFavorite)
			prePersistLocationFavorite((LocationFavorite) entity);
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
		if (entity instanceof Chat)
			postPersistChat((Chat) entity);
		else if (entity instanceof ContactBlock)
			postPersistContactBlock((ContactBlock) entity);
		else if (entity instanceof ContactBluetooth)
			postPersistContactBluetooth((ContactBluetooth) entity);
		else if (entity instanceof ContactLink)
			postPersistContactLink((ContactLink) entity);
		else if (entity instanceof EventParticipate)
			postPersistEventParticipate((EventParticipate) entity);
		else if (entity instanceof Feedback)
			postPersistFeedback((Feedback) entity);
		else if (entity instanceof Location)
			postPersistLocation((Location) entity);
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

	private void prePersistLocationFavorite(LocationFavorite locationFavorite)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		locationFavorite.setFavorite(Boolean.TRUE);
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

	private void preUpdateLocation(Location location) throws Exception {
		if (location.old("address") != null)
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
		if (contact.getVerified()) {
			final QueryParams params = new QueryParams(Query.contact_chat);
			params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId());
			if (repository.list(params).size() == 0) {
				final Chat chat = new Chat();
				chat.setContactId(adminId);
				chat.setContactId2(contact.getId());
				chat.setSeen(false);
				chat.setNote(
						MessageFormat.format(Text.mail_welcome.getText(contact.getLanguage()),
								contact.getPseudonym()));
				repository.save(chat);
			}
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

	private void postPersistContactBlock(final ContactBlock contactBlock)
			throws Exception {
		notificationService.sendEmail(null, "BLOCK " + contactBlock.getContactId2(),
				"id: " + contactBlock.getId() +
						"\ncontactId: " + contactBlock.getContactId() +
						"\ncontactId2: " + contactBlock.getContactId2() +
						"\nreason: " + contactBlock.getReason() +
						"\nnote: " + contactBlock.getNote());
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
				marketing.setType(ContactMarketing.Type.CollectFriends);
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

	private void postPersistLocation(Location location) throws Exception {
		final LocationFavorite locationFavorite = new LocationFavorite();
		locationFavorite.setContactId(location.getContactId());
		locationFavorite.setLocationId(location.getId());
		locationFavorite.setFavorite(true);
		repository.save(locationFavorite);
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