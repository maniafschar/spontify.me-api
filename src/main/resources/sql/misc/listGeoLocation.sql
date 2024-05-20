SELECT
	geoLocation.id,
	geoLocation.latitude,
	geoLocation.longitude,
	geoLocation.street,
	geoLocation.number,
	geoLocation.town,
	geoLocation.zipCode,
	geoLocation.country
FROM
	GeoLocation geoLocation
WHERE
	{search}