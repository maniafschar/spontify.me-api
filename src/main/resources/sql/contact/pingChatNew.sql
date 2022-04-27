SELECT
	chat.contactId,
	count(*) as c
FROM
	Chat chat,
	Contact contact
WHERE
	contact.id={USERID} and
	contact.id=chat.contactId2 and
	chat.seen=false and
	{search}
GROUP BY
	chat.contactId