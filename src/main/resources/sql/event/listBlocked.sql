SELECT
	event.contactId,
	event.endDate,
	event.id,
	event.imageList,
	event.locationId,
	event.maxParticipants,
	event.price,
	event.skills,
	event.skillsText,
	event.startDate,
	event.description,
	event.repetition,
	location.description,
	location.id,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	contact.imageList,
	contact.pseudonym,
	block.id,
	block.note,
	block.reason,
	'' as geolocationDistance
FROM
	Event event left join Location location on event.locationId=location.id,
	Block block,
	Contact contact
WHERE
	block.eventId=event.id and
	block.contactId={USERID} and
	event.contactId=contact.id