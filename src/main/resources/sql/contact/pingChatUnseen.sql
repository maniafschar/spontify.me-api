SELECT
	chat.contactId2,
	count(*) as c
FROM
	Chat chat,
	Contact contact
WHERE
	contact.id={USERID} and
	contact.id=chat.contactId and
	chat.seen=false and
	{search}
GROUP BY
	chat.contactId2