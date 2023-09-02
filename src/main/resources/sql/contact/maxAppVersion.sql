SELECT
	max(contact.version) as c,
	contact.clientId
FROM
	Contact contact
WHERE
	contact.os<>'web' and contact.id<>{USERID} and contact.version is not null
GROUP By
	contact.clientId