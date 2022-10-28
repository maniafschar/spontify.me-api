SELECT
	contact.pseudonym,
	contact.imageList,
	contactNotification.id,
	contactNotification.text,
	contactNotification.action,
	contactNotification.seen,
	contactNotification.createdAt
FROM
	Contact contact,
	ContactNotification contactNotification
WHERE
	contact.id=contactNotification.contactId2 and
	contactNotification.contactId={USERID} and
	{search}
ORDER BY
	contactNotification.createdAt DESC