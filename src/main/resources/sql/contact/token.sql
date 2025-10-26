SELECT
	contactToken.id,
	contactToken.contactId,
	contactToken.modifiedAt
FROM
	ContactToken contactToken
WHERE
	{search}