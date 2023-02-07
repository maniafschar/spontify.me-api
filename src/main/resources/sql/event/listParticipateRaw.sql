select
	eventParticipate.id,
	eventParticipate.contactId,
	eventParticipate.eventId,
	eventParticipate.eventDate,
	eventParticipate.state,
	eventParticipate.reason,
	eventParticipate.modifiedAt
FROM
	EventParticipate eventParticipate,
	Event event,
	Contact contact
WHERE
	contact.id=eventParticipate.contactId and
	event.id=eventParticipate.eventId and
	{search}