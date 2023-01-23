select
	eventParticipate.id,
	eventParticipate.contactId,
	eventParticipate.eventId,
	eventParticipate.eventDate,
	eventParticipate.state,
	eventParticipate.reason,
	eventParticipate.modifiedAt,
	event.category,
	event.id,
	event.startDate,
	event.endDate,
	event.contactId,
	event.locationId,
	event.maxParticipants,
	event.confirm,
	event.price,
	event.visibility,
	event.text,
	event.type,
	event.price,
	contact.age,
	contact.aboutMe,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.id,
	contact.image,
	contact.imageList,
	contact.latitude,
	contact.longitude,
	contact.pseudonym,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	location.name,
	location.address,
	'' as geolocationDistance
FROM
	EventParticipate eventParticipate,
	Event event left join Location location on event.locationId=location.id,
	Contact contact
	left join ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	event.id=eventParticipate.eventId and
	eventParticipate.contactId=contact.id and
	{search}