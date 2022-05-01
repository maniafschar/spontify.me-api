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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactRating;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.entity.Feedback.Type;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationRating;
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

	@PrePersist
	public void prePersist(final BaseEntity entity) throws Exception {
		if (entity instanceof ContactBluetooth)
			prePersistContactBluetooth((ContactBluetooth) entity);
	}

	@PreUpdate
	public void preUpdate(final BaseEntity entity) throws Exception {
		if (entity instanceof Contact)
			preUpdateContact((Contact) entity);
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

	private void preUpdateContact(final Contact contact) {
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
		final Contact feedbackUser = repository.one(Contact.class, feedback.getContactId());
		if (feedback.getType() == Type.FEEDBACK) {
			final Chat chat = new Chat();
			chat.setContactId(feedback.getContactId());
			chat.setContactId2(adminId);
			chat.setNote(feedback.getText());
			chat.setSeen(Boolean.TRUE);
			repository.save(chat);
			notificationService.sendNotification(repository.one(Contact.class, adminId), feedbackUser,
					NotificationID.Feedback, null, feedback.getText());
		}
		notificationService.sendEmail(null, feedback.getType() + ": " + feedbackUser.getPseudonym(),
				new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(feedback));
	}

	private void postPersistContactBluetooth(final ContactBluetooth contactBlutooth)
			throws Exception {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		final Contact other = repository.one(Contact.class, contactBlutooth.getContactId2());
		if (me.getFindMe() && other.getFindMe()) {
			notificationService.sendNotificationOnMatch(NotificationID.FindMe, me, other);
			notificationService.sendNotificationOnMatch(NotificationID.FindMe, other, me);
		}
	}

	private void postPersistChat(final Chat chat) throws Exception {
		final Contact contactFrom = repository.one(Contact.class, chat.getContactId());
		if (chat.getLocationId() == null) {
			final Contact contactTo = repository.one(Contact.class, chat.getContactId2());
			String s = null;
			if (chat.getNote() == null)
				s = Text.mailSentImg.getText(contactTo.getLanguage());
			else {
				s = chat.getNote();
				if (s.indexOf(" :openPos(") == 0)
					s = Text.valueOf("mailSentPos" + contactFrom.getGender()).getText(contactTo.getLanguage());
				else if (s.indexOf(" :open(") == 0)
					s = Text.valueOf("mailSentEntr" + (s.lastIndexOf(" :open(") == 0 ? "y" : "ies"))
							.getText(contactTo.getLanguage());
			}
			notificationService.sendNotification(contactFrom, contactTo, NotificationID.NewMsg,
					"chat=" + contactFrom.getId(), s);
		} else
			notificationService.locationNotifyOnMatch(contactFrom, chat.getLocationId(), NotificationID.ChatLocation,
					chat.getNote());
	}

	private void postUpdateContactLink(ContactLink contactLink) throws Exception {
		if (contactLink.getStatus() == Status.Friends) {
			notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId2()),
					repository.one(Contact.class, contactLink.getContactId()),
					NotificationID.FriendAppro, Strings.encodeParam("p=" + contactLink.getContactId2()));
		}
	}

	private void postPersistContactLink(ContactLink contactLink) throws Exception {
		notificationService.sendNotification(repository.one(Contact.class, contactLink.getContactId()),
				repository.one(Contact.class, contactLink.getContactId2()),
				NotificationID.FriendReq, Strings.encodeParam("p=" + contactLink.getContactId()));
	}

	private void postPersistEventParticipate(EventParticipate eventParticipate) throws Exception {
		final Event event = repository.one(Event.class, eventParticipate.getEventId());
		if (event != null && !event.getContactId().equals(eventParticipate.getContactId())) {
			final Contact contactTo = repository.one(Contact.class, event.getContactId());
			final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
			notificationService.sendNotification(contactFrom, contactTo,
					NotificationID.MarkEvent,
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
				NotificationID.RatingProfile, Strings.encodeParam("p=" + contactRating.getContactId()),
				contactRating.getRating() + "%");
	}

	private void postPersistLocationRating(LocationRating locationRating) throws Exception {
		repository.executeUpdate(
				"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id) where locationId="
						+ locationRating.getLocationId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationRating.getContactId()),
				locationRating.getLocationId(), NotificationID.RatingLocMat,
				repository.one(Location.class, locationRating.getLocationId()).getName());
	}
}