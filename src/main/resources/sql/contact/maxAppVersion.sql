SELECT
	max(contact.version) as c,
	contact.clientId
FROM
	Contact contact
WHERE
	contact.os<>'web' and contact.version is not null
	{search}
GROUP By
	contact.clientId
