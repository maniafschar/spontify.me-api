select
	eventParticipate.id,
	eventParticipate.contactId,
	eventParticipate.eventId,
	eventParticipate.eventDate,
	eventParticipate.state,
	eventParticipate.reason,
	eventParticipate.modifiedAt,
	event.endDate,
	event.id,
	event.locationId,
	event.contactId,
	event.maxParticipants,
	event.price,
	event.startDate,
	event.text,
	event.type,
	event.visibility
FROM
	EventParticipate eventParticipate,
	Event event
WHERE
	event.id=eventParticipate.eventId and
	{search}