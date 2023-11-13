SELECT
	clientMarketingResult.id,
	clientMarketingResult.image,
	clientMarketingResult.storage
FROM
	ClientMarketingResult clientMarketingResult
WHERE
	{search}