select
	location.id,
	location.address,
	location.name,
	location.latitude,
	location.longitude,
	'' as geolocationDistance
FROM
	Location location
WHERE
	{search}
ORDER BY
	location.id
