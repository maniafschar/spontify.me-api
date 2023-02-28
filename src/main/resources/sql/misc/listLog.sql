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
	log.uri,
	log.webCall,
	ip.hostname,
	ip.country,
	ip.region,
	ip.city,
	ip.org,
	ip.timezone,
	ip.postal,
	ip.latitude,
	ip.longitude
FROM
	Log log left join Ip ip on log.ip=ip.ip
WHERE
	{search}
ORDER BY
	log.createdAt DESC,
	log.id DESC