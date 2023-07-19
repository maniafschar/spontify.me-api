SELECT
	event.endDate,
	event.startDate,
	event.repetition,
	location.country,
	location.town
FROM
	Event event,
	Location location,
	Contact contact
WHERE
	location.id=event.locationId and
	contact.id=event.contactId and
	(event.image is not null or location.image is not null) and
	{search}