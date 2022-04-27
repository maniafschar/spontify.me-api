SELECT
	contactLink.id,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.status,
	contactLink.modifiedAt
FROM
	ContactLink contactLink
WHERE
	contactLink.contactId={USERID} and contactLink.contactId2={ID} or
	contactLink.contactId={ID} and contactLink.contactId2={USERID} and
	{search}