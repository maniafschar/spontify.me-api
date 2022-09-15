SELECT
	log.body,
	log.contactId,
	log.createdAt,
	log.id,
	log.ip,
	log.method,
	log.modifiedAt,
	log.port,
	log.query,
	log.referer,
	log.status,
	log.time,
	log.uri
FROM
	Log log
WHERE
	{search}
ORDER BY
	log.createdAt DESC