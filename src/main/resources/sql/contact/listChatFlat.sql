SELECT
	contactChat.id
FROM
	Contact contact,
	ContactChat contactChat
WHERE
	contactChat.contactId2=contact.id and
	{search}