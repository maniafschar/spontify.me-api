SELECT
	event.confirm,
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.maxParticipants,
	event.price,
	event.rating,
	event.skills,
	event.skillsText,
	event.startDate,
	event.text,
	event.type,
	event.url,
	contact.id,
	contact.image,
	contact.imageList,
	contact.pseudonym,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	'' as geolocationDistance
FROM
	Event event,
	Location location,
	Contact contact
WHERE
	location.id=event.locationId and
	contact.id=event.contactId and
	(event.imageList is not null or location.imageList is not null) and
	{search}