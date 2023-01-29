SELECT
	contact.id,
	contact.pseudonym
FROM
	Contact contact,
	ContactChat contactChat
WHERE
	contact.id=contactChat.contactId and
	contactChat.contactId2={USERID} and
	contactChat.seen=false and
	{search}
GROUP BY
	contact.id