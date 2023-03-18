SELECT
	concat(log.method, ' ', SUBSTRING_INDEX(log.uri, '/', 3)) as label,
	sum(log.time)*1.0/count(*) as time,
	count(*)*1.0/(select count(*) from Log l where l.uri not like 'ad%' and l.uri not like 'web%' and l.uri not like '/support/%' and TO_DAYS(createdAt)>TO_DAYS(current_timestamp)-90) as percentage
FROM
	Log log
WHERE
	log.uri not like 'web%' and
	log.uri not like 'ad%' and
	log.uri not like '/support/%' and
	TO_DAYS(log.createdAt)>TO_DAYS(current_timestamp)-90
GROUP BY
	label
ORDER BY
	time