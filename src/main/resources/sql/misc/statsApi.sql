SELECT
	concat(log.method, ' ', SUBSTRING_INDEX(log.uri, '/', 3)) as label,
	(sum(log.time)*1.0+0.5)/count(*) as time,
	count(*)*1.0/(select count(*) from Log l where l.clientId={CLIENTID} and l.uri like '/%' and l.uri not like '/support/%' and l.uri not like '/ws/%' and TO_DAYS(createdAt)>TO_DAYS(current_timestamp)-90) as percentage
FROM
	Log log
WHERE
	log.clientId={CLIENTID} and
	log.uri like '/%' and
	log.uri not like '/support/%' and
	log.uri not like '/ws/%' and
	TO_DAYS(log.createdAt)>TO_DAYS(current_timestamp)-90
GROUP BY
	label
ORDER BY
	time