SELECT
	event.endDate,
	event.id,
	event.imageList,
	event.skills,
	event.skillsText,
	event.startDate,
	event.text,
	event.type,
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