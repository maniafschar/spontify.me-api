SELECT
	count(*) as c
FROM
	ContactVisit contactVisit,
	Contact contact
WHERE
	contactVisit.contactId2=contact.id and
	contactVisit.modifiedAt>=contact.visitPage and
	contact.id={USERID} and
	{search}