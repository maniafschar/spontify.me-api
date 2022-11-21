SELECT
	count(*)/(select count(*) from Location where createdAt>'2018-1-1')*1000 as c,
	location.town,
	location.category,
	max(location.createdAt)
FROM
	Location location
WHERE
	location.createdAt>'2018-1-1'
GROUP BY
	location.country,
	location.town,
	location.category
ORDER BY
	c DESC