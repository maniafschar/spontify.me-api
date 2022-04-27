SELECT
	count(*) as c
FROM
	ContactLink contactLink,
	Contact contact
WHERE
	contactLink.contactId2=contact.id and
	contact.id={USERID} and
	contactLink.status='Pending' and
	{search}