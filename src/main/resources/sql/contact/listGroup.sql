SELECT
	contactGroup.id,
	contactGroup.name,
	contactGroup.contactId,
	contactGroup.modifiedAt
FROM
	ContactGroup contactGroup
WHERE
	{search}