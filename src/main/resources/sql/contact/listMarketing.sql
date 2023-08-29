SELECT
	contactMarketing.id,
	contactMarketing.clientMarketingId,
	contactMarketing.finished,
	contactMarketing.storage
FROM
	ContactMarketing contactMarketing
WHERE
	{search}