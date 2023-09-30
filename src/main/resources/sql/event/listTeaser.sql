SELECT
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
	event.description,
	event.repetition,
	event.url,
	contact.id,
	contact.image,
	contact.imageList,
	contact.pseudonym,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.town,
	'' as geolocationDistance
FROM
	Event event left join Location location on location.id=event.locationId,
	Contact contact
WHERE
	contact.id=event.contactId and
	contact.clientId={CLIENTID} and
	(event.image is not null or location.image is not null or contact.image is not null) and
	{search}