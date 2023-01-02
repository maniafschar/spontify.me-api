SELECT
	case when cast((log.time-10)/20 as integer)>20 then 20 else cast((log.time-10)/20 as integer) end as time,
	count(*)*1.0/(select count(*) from Log where uri not like '/support/%' and uri not like 'ad%' and uri not like 'web%' and TO_DAYS(createdAt)>TO_DAYS(current_timestamp)-90) as count
FROM
	Log log
WHERE
	log.uri not like 'ad%' and
	log.uri not like 'web%' and
	log.uri not like '/support/%' and
	TO_DAYS(log.createdAt)>TO_DAYS(current_timestamp)-90
GROUP BY
	time
ORDER BY
	time