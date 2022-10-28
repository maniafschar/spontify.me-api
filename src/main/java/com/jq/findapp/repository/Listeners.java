package com.jq.findapp.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBlock;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.entity.LocationRating;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.repository.listener.AbstractRepositoryListener;
import com.jq.findapp.repository.listener.ChatListener;
import com.jq.findapp.repository.listener.ContactBlockListener;
import com.jq.findapp.repository.listener.ContactBluetoothListener;
import com.jq.findapp.repository.listener.ContactGeoLocationHistoryListener;
import com.jq.findapp.repository.listener.ContactLinkListener;
import com.jq.findapp.repository.listener.ContactListener;
import com.jq.findapp.repository.listener.ContactVisitListener;
import com.jq.findapp.repository.listener.ContactWhatToDoListener;
import com.jq.findapp.repository.listener.EventListener;
import com.jq.findapp.repository.listener.EventParticipateListener;
import com.jq.findapp.repository.listener.LocationFavoriteListener;
import com.jq.findapp.repository.listener.LocationListener;
import com.jq.findapp.repository.listener.LocationRatingListener;
import com.jq.findapp.repository.listener.LocationVisitListener;
import com.jq.findapp.repository.listener.TicketListener;

@Component
public class Listeners {
	@Autowired
	private ApplicationContext applicationContext;

	private <T extends BaseEntity> AbstractRepositoryListener<T> entity2listener(T entity) {
		if (entity instanceof Chat)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ChatListener.class);
		if (entity instanceof ContactBlock)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactBlockListener.class);
		if (entity instanceof ContactBluetooth)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactBluetoothListener.class);
		if (entity instanceof ContactGeoLocationHistory)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactGeoLocationHistoryListener.class);
		if (entity instanceof ContactLink)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactLinkListener.class);
		if (entity instanceof Contact)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactListener.class);
		if (entity instanceof ContactVisit)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactVisitListener.class);
		if (entity instanceof ContactWhatToDo)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactWhatToDoListener.class);
		if (entity instanceof Event)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(EventListener.class);
		if (entity instanceof EventParticipate)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(EventParticipateListener.class);
		if (entity instanceof LocationFavorite)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationFavoriteListener.class);
		if (entity instanceof Location)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationListener.class);
		if (entity instanceof LocationRating)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationRatingListener.class);
		if (entity instanceof LocationVisit)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationVisitListener.class);
		if (entity instanceof Ticket)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(TicketListener.class);
		return null;
	}

	<T extends BaseEntity> void prePersist(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.prePersist(entity);
	}

	<T extends BaseEntity> void postPersist(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.postPersist(entity);
	}

	<T extends BaseEntity> void preUpdate(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.preUpdate(entity);
	}

	<T extends BaseEntity> void postUpdate(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.postUpdate(entity);
	}

	<T extends BaseEntity> void preRemove(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.preRemove(entity);
	}

	<T extends BaseEntity> void postRemove(T entity) throws Exception {
		final AbstractRepositoryListener<T> listener = entity2listener(entity);
		if (listener != null)
			listener.postRemove(entity);
	}
}