SELECT
	contact.id,
	contact.pseudonym,
	contact.imageList,
	contactNotification.id,
	contactNotification.text,
	contactNotification.action,
	contactNotification.seen,
	contactNotification.createdAt
FROM
	ContactNotification contactNotification left join Contact contact on contactNotification.contactId2=contact.id
WHERE
	contactNotification.contactId={USERID} and
	{search}
ORDER BY
	contactNotification.createdAt DESC
