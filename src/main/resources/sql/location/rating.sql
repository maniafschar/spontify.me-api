SELECT
	locationRating.id,
	locationRating.contactId,
	locationRating.createdAt,
	locationRating.locationId,
	locationRating.rating,
	locationRating.text,
	locationRating.paid,
	locationRating.modifiedAt,
	locationRating.image,
	contact.pseudonym,
	location.name,
	location.image,
	location.bonus
FROM
	LocationRating locationRating,
	Contact contact,
	Location location
WHERE
	locationRating.contactId=contact.id and
	locationRating.locationId=location.id and
	{search}
ORDER BY
	locationRating.id asc