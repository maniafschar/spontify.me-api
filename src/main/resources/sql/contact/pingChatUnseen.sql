SELECT
	contactChat.contactId2,
	count(*) as c
FROM
	ContactChat contactChat,
	Contact contact
WHERE
	contact.id={USERID} and
	contact.id=contactChat.contactId and
	contactChat.seen=false and
	{search}
GROUP BY
	contactChat.contactId2