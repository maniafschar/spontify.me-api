SELECT
	contactMarketing.id,
	contactMarketing.contactId,
	contactMarketing.clientMarketingId,
	contactMarketing.finished,
	contactMarketing.storage
FROM
	ContactMarketing contactMarketing
WHERE
	{search}