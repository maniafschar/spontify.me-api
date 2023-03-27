select
	eventParticipate.id,
	eventParticipate.contactId,
	eventParticipate.eventId,
	eventParticipate.eventDate,
	eventParticipate.state,
	eventParticipate.reason,
	eventParticipate.modifiedAt,
	event.id,
	event.endDate,
	event.contactId,
	event.locationId,
	event.maxParticipants,
	event.confirm,
	event.price,
	event.skills,
	event.skillsText,
	event.startDate,
	event.description,
	event.type,
	event.url,
	contact.age,
	contact.authenticate,
	contact.description,
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
	location.address,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.rating,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance
FROM
	EventParticipate eventParticipate,
	Event event left join Location location on event.locationId=location.id left join
	LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact
	left join ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	event.id=eventParticipate.eventId and
	{search}