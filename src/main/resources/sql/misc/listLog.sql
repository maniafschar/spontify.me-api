SELECT
	log.id,
	log.method,
	log.query,
	log.body,
	log.uri,
	log.contactId,
	log.time,
	log.status,
	log.port,
	log.createdAt
FROM
	Log log
WHERE
	{search}
ORDER BY
	log.id DESC