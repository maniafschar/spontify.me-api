SELECT
	contactReferer.id,
	contactReferer.contactId,
	contactReferer.footprint,
	contactReferer.ip,
	contactReferer.createdAt,
	contactReferer.modifiedAt
FROM
	ContactReferer contactReferer
WHERE
	{search}