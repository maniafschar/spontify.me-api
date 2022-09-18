SELECT
	chat.id,
	chat.contactId,
	chat.contactId2,
	chat.createdAt,
	chat.image,
	chat.note,
	chat.action,
	chat.textId,
	chat.seen,
	(select pseudonym from Contact contact where chat.locationId is not null and contact.id=chat.contactId) as pseudonym
FROM
	Chat chat
WHERE
	{search}
GROUP BY
	chat.id
ORDER BY
	chat.createdAt desc