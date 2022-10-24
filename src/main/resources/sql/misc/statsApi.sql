SELECT
	concat(log.method, ' ', SUBSTRING_INDEX(log.uri, '/', 3)) as label,
	sum(log.time)*1.0/count(*) as time,
	count(*)*1.0/(select count(*) from Log where uri<>'web' and uri<>'ad' and uri not like '/support/%' and createdAt>'2022-09-01') as percentage
FROM
	Log log
WHERE
	log.uri<>'web' and
	log.uri<>'ad' and
	log.uri not like '/support/%' and
	log.createdAt>'2022-09-01'
GROUP BY
	label
ORDER BY
	time