SELECT
	eventRating.id,
	eventRating.createdAt,
	eventRating.rating,
	eventRating.description,
	eventRating.modifiedAt,
	eventRating.image,
	eventParticipate.id,
	eventParticipate.eventDate,
	eventParticipate.state,
	event.id,
	contact.id,
	contact.imageList,
	contact.pseudonym
FROM
	EventRating eventRating,
	EventParticipate eventParticipate,
	Event event,
	Contact contact
WHERE
	eventParticipate.id=eventRating.eventParticipateId and
	event.id=eventParticipate.eventId and
	contact.id=eventParticipate.contactId and
	{search}
ORDER BY
	eventRating.id desc