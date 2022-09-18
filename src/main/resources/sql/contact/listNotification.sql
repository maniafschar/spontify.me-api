SELECT
	contact.pseudonym,
	contactNotification.id,
	contactNotification.text,
	contactNotification.action,
	contactNotification.seen,
	contactNotification.createdAt
FROM
	Contact contact,
	ContactNotification contactNotification
WHERE
	contactNotification.contactId2=contact.id and
	contactNotification.contactId={USERID} and
	{search}
ORDER BY
	contactNotification.createdAt DESC