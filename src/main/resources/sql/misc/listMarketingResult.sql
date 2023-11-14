SELECT
	clientMarketingResult.id,
	clientMarketingResult.image,
	clientMarketingResult.storage,
	clientMarketing.id,
	clientMarketing.startDate,
	clientMarketing.endDate
FROM
	ClientMarketing clientMarketing,
	ClientMarketingResult clientMarketingResult
WHERE
	clientMarketingResult.clientMarketingId=clientMarketing.id and
	{search}