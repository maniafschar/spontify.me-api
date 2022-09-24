SELECT
	contact.id as c
FROM
	Chat chat,
	Contact contact
WHERE
	(
		chat.contactId={USERID} and contact.id=chat.contactId2
		or 
		chat.contactId2={USERID} and contact.id=chat.contactId
	) and contact.id<>{USERID} and contact.verified=1 and
	{search}
GROUP BY
	contact.id