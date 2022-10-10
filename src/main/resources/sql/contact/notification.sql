SELECT
	contactNotification.id,
	contactNotification.contactId,
	contactNotification.contactId2,
	contactNotification.text,
	contactNotification.createdAt,
	contactNotification.action,
	contactNotification.seen,
	contactNotification.textType,
	contact.pseudonym
FROM
	ContactNotification contactNotification,
	Contact contact
WHERE
	contact.id=contactNotification.contactId and
	{search}