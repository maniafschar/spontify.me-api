SELECT
	contactGeoLocationHistory.id,
	contactGeoLocationHistory.contactId,
	contactGeoLocationHistory.geoLocationId,
	contactGeoLocationHistory.altitude,
	contactGeoLocationHistory.heading,
	contactGeoLocationHistory.speed,
	contactGeoLocationHistory.accuracy,
	contactGeoLocationHistory.manual
FROM
	ContactGeoLocationHistory contactGeoLocationHistory
WHERE
	{search}
ORDER BY
	contactGeoLocationHistory.id DESC