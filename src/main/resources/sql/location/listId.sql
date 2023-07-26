select
	location.id,
	location.latitude,
	location.longitude,
	'' as geolocationDistance
FROM
	Location location
WHERE
	{search}