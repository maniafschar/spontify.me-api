SELECT
	case when cast((log.time-5+0.5)/10 as integer)>20 then 20 else cast((log.time-5+0.5)/10 as integer) end as time,
	count(*)*1.0/(select count(*) from Log where clientId={CLIENTID} and uri like '/%' and uri not like '/support/%' and uri not like '/ws/%' and TO_DAYS(createdAt)>TO_DAYS(current_timestamp)-90) as count
FROM
	Log log
WHERE
	log.clientId={CLIENTID} and
	log.uri like '/%' and
	log.uri not like '/support/%' and
	log.uri not like '/ws/%' and
	TO_DAYS(log.createdAt)>TO_DAYS(current_timestamp)-90
GROUP BY
	time
ORDER BY
	time