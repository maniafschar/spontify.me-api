SELECT
	count(*) as c
FROM
	ContactNotification contactNotification,
	Contact contact
WHERE
	contactNotification.contactId=contact.id and
	contactNotification.textId<>'FriendshipRequest' and
	contactNotification.seen=false and
	contact.id={USERID} and
	{search}