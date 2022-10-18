package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.service.WhatToDoService;

@Component
public class ContactWhatToDoListener extends AbstractRepositoryListener {
	private static WhatToDoService whatToDoService;

	@Autowired
	private void setWhatToDoService(WhatToDoService whatToDoService) {
		ContactWhatToDoListener.whatToDoService = whatToDoService;
	}

	@PostUpdate
	public void postUpdate(ContactWhatToDo contactWhatToDo) throws Exception {
		whatToDoService.findAndNotify(contactWhatToDo);
	}

	@PostPersist
	public void postPersist(ContactWhatToDo contactWhatToDo) throws Exception {
		whatToDoService.findAndNotify(contactWhatToDo);
	}
}