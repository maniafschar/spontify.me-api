package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.service.WhatToDoService;

@Component
public class ContactWhatToDoListener extends AbstractRepositoryListener<ContactWhatToDo> {
	@Autowired
	private WhatToDoService whatToDoService;

	@Override
	public void postUpdate(final ContactWhatToDo contactWhatToDo) throws Exception {
		whatToDoService.findMatchingSpontis(contactWhatToDo);
	}

	@Override
	public void postPersist(final ContactWhatToDo contactWhatToDo) throws Exception {
		whatToDoService.findMatchingSpontis(contactWhatToDo);
	}
}