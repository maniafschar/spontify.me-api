select
	eventParticipate.id,
	eventParticipate.contactId,
	eventParticipate.eventId,
	eventParticipate.eventDate,
	eventParticipate.state,
	eventParticipate.reason,
	eventParticipate.modifiedAt
FROM
	EventParticipate eventParticipate
WHERE
	{search}