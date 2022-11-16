SELECT
	contact.id,
	contact.gender,
	contact.imageList,
	contact.pseudonym,
	max(chat.id) as chatId,
	max(chat.createdAt) as maxDate,
	CASE WHEN chat.contactId={USERID} THEN 0 ELSE chat.contactId END as contactId,
	sum(CASE WHEN chat.seen=true OR chat.contactId={USERID} THEN 0 ELSE 1 END) as unseen,
	sum(CASE WHEN chat.seen=true OR chat.contactId<>{USERID} THEN 0 ELSE 1 END) as unseen2
FROM
	Contact contact,
	Chat chat
WHERE
	(
		(chat.contactId2={USERID} and contact.id=chat.contactId)
		or
		(chat.contactId={USERID} and contact.id=chat.contactId2)
	) and contact.id<>{USERID} and contact.verified=1 and
	{search}
GROUP BY
	contact.id
ORDER BY
	maxDate desc,
	chatId desc