package com.jq.findapp.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Block;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.EventRating;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.repository.listener.AbstractRepositoryListener;
import com.jq.findapp.repository.listener.BlockListener;
import com.jq.findapp.repository.listener.ClientMarketingListener;
import com.jq.findapp.repository.listener.ClientNewsListener;
import com.jq.findapp.repository.listener.ContactBluetoothListener;
import com.jq.findapp.repository.listener.ContactChatListener;
import com.jq.findapp.repository.listener.ContactGeoLocationHistoryListener;
import com.jq.findapp.repository.listener.ContactLinkListener;
import com.jq.findapp.repository.listener.ContactListener;
import com.jq.findapp.repository.listener.ContactMarketingListener;
import com.jq.findapp.repository.listener.ContactVisitListener;
import com.jq.findapp.repository.listener.EventListener;
import com.jq.findapp.repository.listener.EventParticipateListener;
import com.jq.findapp.repository.listener.EventRatingListener;
import com.jq.findapp.repository.listener.LocationFavoriteListener;
import com.jq.findapp.repository.listener.LocationListener;
import com.jq.findapp.repository.listener.LocationVisitListener;
import com.jq.findapp.repository.listener.LogListener;
import com.jq.findapp.repository.listener.TicketListener;

@Component
class Listeners {
	@Autowired
	private ApplicationContext applicationContext;

	private static final AbstractRepositoryListener<?> LISTENER = new AbstractRepositoryListener<>() {
	};

	private <T extends BaseEntity> AbstractRepositoryListener<T> entity2listener(final T entity) {
		if (entity instanceof Block)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(BlockListener.class);
		if (entity instanceof ClientMarketing)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ClientMarketingListener.class);
		if (entity instanceof ClientNews)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ClientNewsListener.class);
		if (entity instanceof Contact)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactListener.class);
		if (entity instanceof ContactBluetooth)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactBluetoothListener.class);
		if (entity instanceof ContactChat)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactChatListener.class);
		if (entity instanceof ContactGeoLocationHistory)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactGeoLocationHistoryListener.class);
		if (entity instanceof ContactLink)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactLinkListener.class);
		if (entity instanceof ContactMarketing)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactMarketingListener.class);
		if (entity instanceof ContactVisit)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(ContactVisitListener.class);
		if (entity instanceof Event)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(EventListener.class);
		if (entity instanceof EventParticipate)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(EventParticipateListener.class);
		if (entity instanceof EventRating)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(EventRatingListener.class);
		if (entity instanceof Location)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationListener.class);
		if (entity instanceof LocationFavorite)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationFavoriteListener.class);
		if (entity instanceof LocationVisit)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LocationVisitListener.class);
		if (entity instanceof Log)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(LogListener.class);
		if (entity instanceof Ticket)
			return (AbstractRepositoryListener<T>) applicationContext.getBean(TicketListener.class);
		return (AbstractRepositoryListener<T>) LISTENER;
	}

	<T extends BaseEntity> void prePersist(final T entity) throws IllegalArgumentException {
		entity2listener(entity).prePersist(entity);
	}

	<T extends BaseEntity> void postPersist(final T entity) throws IllegalArgumentException {
		entity2listener(entity).postPersist(entity);
	}

	<T extends BaseEntity> void preUpdate(final T entity) throws IllegalArgumentException {
		entity2listener(entity).preUpdate(entity);
	}

	<T extends BaseEntity> void postUpdate(final T entity) throws IllegalArgumentException {
		entity2listener(entity).postUpdate(entity);
	}

	<T extends BaseEntity> void preRemove(final T entity) throws IllegalArgumentException {
		entity2listener(entity).preRemove(entity);
	}

	<T extends BaseEntity> void postRemove(final T entity) throws IllegalArgumentException {
		entity2listener(entity).postRemove(entity);
	}
}
