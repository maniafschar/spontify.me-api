SELECT
	case when cast((log.time-5)/10 as integer)>20 then 20 else cast((log.time-5)/10 as integer) end as time,
	count(*)*1.0/(select count(*) from Log where uri not like '/support/%' and uri<>'web' and uri<>'ad' and createdAt>'2022-09-01') as count
FROM
	Log log
WHERE
	log.uri<>'ad' and log.uri<>'web' and log.uri not like '/support/%' and log.createdAt>DATEADD(MONTH, -3, current_timestamp)
GROUP BY
	time
ORDER BY
	time