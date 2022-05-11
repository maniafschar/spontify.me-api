SELECT
	max(contactGeoLocationHistory.id) as id,
	contactGeoLocationHistory.latitude,
	contactGeoLocationHistory.longitude,
	contactGeoLocationHistory.altitude,
	contactGeoLocationHistory.heading,
	contactGeoLocationHistory.speed,
	contactGeoLocationHistory.accuracy,
	contactGeoLocationHistory.street,
	contactGeoLocationHistory.town,
	contactGeoLocationHistory.zipCode,
	contactGeoLocationHistory.country
FROM
	ContactGeoLocationHistory contactGeoLocationHistory
WHERE
	{search}
GROUP BY
	contactGeoLocationHistory.latitude,
	contactGeoLocationHistory.longitude