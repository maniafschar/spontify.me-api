SELECT
	contactMarketing.createdAt,
	contactMarketing.contactId,
	contactMarketing.clientMarketingId,
	contactMarketing.finished,
	contactMarketing.id,
	contactMarketing.modifiedAt,
	contactMarketing.storage
FROM
	ContactMarketing contactMarketing
WHERE
	{search}