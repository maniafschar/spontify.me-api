SELECT
	contact.id,
	contact.gender,
	contact.imageList,
	contact.pseudonym,
	max(chat.createdAt) as maxDate,
	sum(CASE WHEN chat.seen=true OR chat.contactId={USERID} THEN 0 ELSE 1 END) as unseen
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
	maxDate desc