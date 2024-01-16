SELECT
	max(contact.version) as c,
	contact.clientId
FROM
	Contact contact,
	Client client
WHERE
	contact.os<>'web' and contact.version is not null and
	client.id=contact.clientId and
	{search}
GROUP By
	contact.clientId
