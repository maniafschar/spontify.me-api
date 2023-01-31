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
	Contact contact
WHERE
	contact.id=eventParticipate.contactId and
	{search}