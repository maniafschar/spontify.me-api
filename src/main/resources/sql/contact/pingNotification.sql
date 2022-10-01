SELECT
	count(*) as c
FROM
	ContactNotification contactNotification,
	Contact contact
WHERE
	contactNotification.contactId=contact.id and
	contactNotification.seen=false and
	contact.id={USERID} and
	(select cb.id from ContactBlock cb where cb.contactId=contactNotification.contactId2 and cb.contactId2={USERID} or cb.contactId={USERID} and cb.contactId2=contactNotification.contactId2) is null and
	{search}