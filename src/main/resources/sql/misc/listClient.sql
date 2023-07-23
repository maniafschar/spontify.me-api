SELECT
	client.id,
	client.appConfig,
	client.css,
	client.name,
	client.email,
	client.url
FROM
	Client client
WHERE
	{search}