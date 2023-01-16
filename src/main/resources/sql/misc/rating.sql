SELECT
	eventRating.id,
	eventRating.createdAt,
	eventRating.rating,
	eventRating.text,
	eventRating.eventId,
	eventRating.modifiedAt,
	eventRating.image,
	event.locationId,
	contact.id,
	contact.imageList,
	contact.pseudonym
FROM
	EventRating eventRating,
	Event event,
	Contact contact
WHERE
	event.id=eventRating.eventId and
	contact.id=event.contactId and
	{search}
ORDER BY
	eventRating.id asc