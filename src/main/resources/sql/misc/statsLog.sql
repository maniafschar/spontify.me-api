SELECT
	cast(log.time-5/10 as integer) as time,
	count(*) as count,
	concat(YEAR(log.createdAt),'-',MONTH(log.createdAt),'-',DAY(log.createdAt)) as date
FROM
	Log log
WHERE
	log.uri not like '/support/%' and log.createdAt>'2022-09-01'
GROUP BY
	date,
	time