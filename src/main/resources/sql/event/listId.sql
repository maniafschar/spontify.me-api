SELECT
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.maxParticipants,
	event.price,
	event.publish,
	event.publishId,
	event.rating,
	event.skills,
	event.skillsText,
	event.startDate,
	event.type,
	event.description,
	event.repetition,
	event.url
FROM
	Event event left join 
	Location location on event.locationId=location.id,
	Contact contact
WHERE
	event.contactId=contact.id and
	{search}
