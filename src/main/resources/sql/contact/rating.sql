select
	contact.pseudonym,
	contactRating.id,
	contactRating.contactId,
	contactRating.createdAt,
	contactRating.rating,
	contactRating.text,
	contactRating.modifiedAt
FROM
	ContactRating contactRating,
	Contact contact
WHERE
	contactRating.contactId=contact.id and
	{search}
ORDER BY
	contactRating.modifiedAt asc