SELECT
	max(contactGeoLocationHistory.id) as id
FROM
	ContactGeoLocationHistory contactGeoLocationHistory
WHERE
	{search}
GROUP BY
	contactGeoLocationHistory.latitude,
	contactGeoLocationHistory.longitude