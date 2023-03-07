SELECT
	contact.id,
	contact.authenticate,
	contact.gender,
	contact.imageList,
	contact.pseudonym,
	max(contactChat.id) as chatId,
	max(contactChat.createdAt) as maxDate,
	(select contactId from ContactChat where id=max(contactChat.id)) as contactId,
	sum(CASE WHEN contactChat.seen=true OR contactChat.contactId={USERID} THEN 0 ELSE 1 END) as unseen,
	sum(CASE WHEN contactChat.seen=true OR contactChat.contactId<>{USERID} THEN 0 ELSE 1 END) as unseen2
FROM
	Contact contact,
	ContactChat contactChat
WHERE
	(
		(contactChat.contactId2={USERID} and contact.id=contactChat.contactId)
		or
		(contactChat.contactId={USERID} and contact.id=contactChat.contactId2)
	) and contact.id<>{USERID} and contact.verified=1 and
	{search}
GROUP BY
	contact.id
ORDER BY
	maxDate desc,
	chatId desc