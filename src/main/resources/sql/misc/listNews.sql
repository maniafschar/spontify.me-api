SELECT
	clientNews.id,
	clientNews.clientId,
	clientNews.publish,
	clientNews.image,
	clientNews.url,
	clientNews.category,
	clientNews.source,
	clientNews.createdAt,
	clientNews.modifiedAt,
	clientNews.description
FROM
	ClientNews clientNews
WHERE
	clientNews.clientId={CLIENTID} and
	{search}
ORDER BY
	clientNews.publish DESC