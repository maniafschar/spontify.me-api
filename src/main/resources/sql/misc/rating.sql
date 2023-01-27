SELECT
	eventRating.id,
	eventRating.contactId,
	eventRating.createdAt,
	eventRating.rating,
	eventRating.text,
	eventRating.eventId,
	eventRating.modifiedAt,
	eventRating.image,
	contact.imageList,
	contact.pseudonym
FROM
	EventRating eventRating,
	Event event,
	Contact contact
WHERE
	event.id=eventRating.eventId and
	contact.id=eventRating.contactId and
	{search}
ORDER BY
	eventRating.id asc