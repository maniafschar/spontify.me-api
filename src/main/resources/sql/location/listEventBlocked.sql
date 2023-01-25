SELECT
	event.confirm,
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
	event.text,
	event.type,
	event.visibility,
	location.category,
	location.description,
	location.id,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	block.id,
	block.note,
	block.reason,
	'' as geolocationDistance
FROM
	Event event,
	Location location,
	Block block
WHERE
	block.eventId=event.id and
	block.contactId={USERID} and
	event.locationId=location.id