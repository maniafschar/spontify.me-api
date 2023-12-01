SELECT
	count(*)/(select count(*) from Location where createdAt>'2018-1-1' and location.town is not null)*1000 as c,
	location.town,
	max(location.createdAt)
FROM
	Location location
WHERE
	location.createdAt>'2018-1-1' and
	location.town is not null
GROUP BY
	location.country,
	location.town
ORDER BY
	c DESC