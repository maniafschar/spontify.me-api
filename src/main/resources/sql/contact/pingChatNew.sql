SELECT
	contact.id,
	contact.pseudonym
FROM
	Contact contact,
	Chat chat
WHERE
	contact.id=chat.contactId and
	chat.contactId2={USERID} and
	chat.seen=false and
	{search}
GROUP BY
	contact.id