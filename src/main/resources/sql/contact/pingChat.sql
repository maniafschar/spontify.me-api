SELECT
	max(chat.id) as c
FROM
	Chat chat,
	Contact contact
WHERE
	(
		(chat.contactId2={USERID} and contact.id=chat.contactId)
		or
		(chat.contactId={USERID} and contact.id=chat.contactId2)
	) and contact.id<>{USERID} and contact.verified=1 and
	{search}