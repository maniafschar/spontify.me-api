SELECT
	contactMarketing.id,
	contactMarketing.contactId,
	contactMarketing.clientMarketingId,
	contactMarketing.storage
FROM
	ContactMarketing contactMarketing
WHERE
	{search}