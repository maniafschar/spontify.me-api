SELECT
	count(*) as c
FROM
	ContactNotification contactNotification,
	Contact contact
WHERE
	contact.id=contactNotification.contactId2 and
	contactNotification.contactId={USERID} and
	contactNotification.seen=false and
	(select b.id from Block b where b.contactId=contactNotification.contactId2 and b.contactId2={USERID} or b.contactId={USERID} and b.contactId2=contactNotification.contactId2) is null and
	{search}