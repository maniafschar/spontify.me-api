SELECT
	contactToken.id,
	contactToken.modifiedAt
FROM
	ContactToken contactToken
WHERE
	{search}