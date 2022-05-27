SELECT
	max(geoLocation.id) as id
FROM
	GeoLocation geoLocation
WHERE
	{search}
GROUP BY
	geoLocation.latitude,
	geoLocation.longitude