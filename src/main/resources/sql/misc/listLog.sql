SELECT
	log.id,
	log.method,
	log.query,
	log.body,
	log.uri,
	log.ip,
	log.contactId,
	log.time,
	log.status,
	log.port,
	log.modifiedAt,
	log.createdAt
FROM
	Log log
WHERE
	{search}
ORDER BY
	log.id DESC