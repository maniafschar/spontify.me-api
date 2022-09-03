SELECT
	contact.id
FROM
	Contact contact
WHERE
	contact.verified=true and
	{search}