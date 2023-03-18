SELECT
	max(contactChat.id) as c
FROM
	ContactChat contactChat,
	Contact contact
WHERE
	(
		(contactChat.contactId2={USERID} and contact.id=contactChat.contactId)
		or
		(contactChat.contactId={USERID} and contact.id=contactChat.contactId2)
	) and contact.id<>{USERID} and contact.verified=true and
	{search}