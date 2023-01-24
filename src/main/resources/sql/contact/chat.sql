SELECT
	contactChat.id,
	contactChat.contactId,
	contactChat.contactId2,
	contactChat.createdAt,
	contactChat.image,
	contactChat.note,
	contactChat.action,
	contactChat.textId,
	contactChat.seen
FROM
	ContactChat contactChat
WHERE
	{search}
GROUP BY
	contactChat.id
ORDER BY
	contactChat.createdAt desc,
	contactChat.id desc