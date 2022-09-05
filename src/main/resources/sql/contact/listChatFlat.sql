SELECT
	chat.id
FROM
	Contact contact,
	Chat chat
WHERE
	chat.contactId2=contact.id and
	{search}