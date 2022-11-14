SELECT
	event.confirm,
	event.contactId,
	event.endDate,
	event.id,
	event.imageList,
	event.locationId,
	event.maxParticipants,
	event.price,
	event.startDate,
	event.text,
	event.type,
	event.visibility,
	location.attr0,
	location.attr1,
	location.attr2,
	location.attr3,
	location.attr4,
	location.attr5,
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