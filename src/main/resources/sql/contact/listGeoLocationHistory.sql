SELECT
	contactGeoLocationHistory.id,
	contactGeoLocationHistory.contactId,
	contactGeoLocationHistory.geoLocationId,
	contactGeoLocationHistory.altitude,
	contactGeoLocationHistory.heading,
	contactGeoLocationHistory.speed,
	contactGeoLocationHistory.accuracy
FROM
	ContactGeoLocationHistory contactGeoLocationHistory
WHERE
	{search}