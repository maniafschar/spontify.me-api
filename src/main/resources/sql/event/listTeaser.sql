SELECT
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.latitude,
	event.longitude,
	event.maxParticipants,
	event.price,
	event.rating,
	event.skills,
	event.skillsText,
	event.startDate,
	event.description,
	event.repetition,
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
	location.town,
	'' as geolocationDistance
FROM
	Event event left join Location location on location.id=event.locationId,
	Contact contact
WHERE
	contact.id=event.contactId and
	contact.clientId={CLIENTID} and
	(event.type='Poll' or length(event.image)>0 or length(location.image)>0 or length(contact.image)>0) and
	{search}