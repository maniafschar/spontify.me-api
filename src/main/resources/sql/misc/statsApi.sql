SELECT
	concat(log.method, ' ', SUBSTRING_INDEX(log.uri, '/', 3)) as label,
	(sum(log.time)*1.0+0.5)/count(*) as time,
	count(*)*1.0/(select count(*) from Log l where l.clientId={CLIENTID} and l.uri like '/%' and l.uri not like '/support/%' and l.uri not like '/marketing/%' and l.uri not like '/ws/%' and cast(TO_DAYS(createdAt) as integer)>cast(TO_DAYS(current_timestamp) as integer)-90) as percentage
FROM
	Log log
WHERE
	log.clientId={CLIENTID} and
	log.uri like '/%' and
	log.uri not like '/support/%' and
	log.uri not like '/ws/%' and
	cast(TO_DAYS(log.createdAt) as integer)>cast(TO_DAYS(current_timestamp) as integer)-90
GROUP BY
	label
ORDER BY
	time