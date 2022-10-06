SELECT
	max(contact.version) as c
FROM
	Contact contact
WHERE
	contact.os <> 'web' and contact.version is not null