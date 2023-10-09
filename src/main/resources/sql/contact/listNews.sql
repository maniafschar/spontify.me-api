SELECT
	contactNews.id,
	contactNews.clientId,
	contactNews.publish,
	contactNews.image,
	contactNews.url,
	contactNews.createdAt,
	contactNews.modifiedAt,
	contactNews.description
FROM
	ContactNews contactNews
WHERE
	contactNews.clientId={CLIENTID} and
	{search}
ORDER BY
	contactNews.publish DESC