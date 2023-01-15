SELECT
	eventRating.id,
	eventRating.contactId,
	eventRating.createdAt,
	eventRating.locationId,
	eventRating.rating,
	eventRating.text,
	eventRating.eventId,
	eventRating.modifiedAt,
	eventRating.image,
	contact.pseudonym,
	location.name,
	location.image,
	location.bonus
FROM
	EventRating eventRating,
	Contact contact,
	Location location
WHERE
	eventRating.contactId=contact.id and
	eventRating.locationId=location.id and
	{search}
ORDER BY
	eventRating.id asc