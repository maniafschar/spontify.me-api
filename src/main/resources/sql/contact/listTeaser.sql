SELECT
	contact.imageList,
	contact.pseudonym
FROM
	Contact contact
WHERE
	contact.verified=1 and
	contact.imageList is not null and
	{search}