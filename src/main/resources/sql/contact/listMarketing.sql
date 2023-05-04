SELECT
	contactMarketing.id,
	contactMarketing.clientMarketingId,
	contactMarketing.storage
FROM
	ContactMarketing contactMarketing
WHERE
	{search}