SELECT
	contactGeoLocationHistory.id,
	contactGeoLocationHistory.contactId,
	contactGeoLocationHistory.geoLocationId,
	contactGeoLocationHistory.altitude,
	contactGeoLocationHistory.heading,
	contactGeoLocationHistory.speed,
	contactGeoLocationHistory.accuracy,
	contactGeoLocationHistory.manual,
	contactGeoLocationHistory.createdAt,
	geoLocation.latitude,
	geoLocation.longitude,
	geoLocation.street,
	geoLocation.number,
	geoLocation.town,
	geoLocation.zipCode,
	geoLocation.country
FROM
	ContactGeoLocationHistory contactGeoLocationHistory,
	GeoLocation geoLocation,
	Contact contact
WHERE
	contactGeoLocationHistory.geoLocationId=geoLocation.id and
	contactGeoLocationHistory.contactId=contact.id and
	contactGeoLocationHistory.id=(select max(id) from ContactGeoLocationHistory where contactId=contact.id) and
	{search}
ORDER BY
	contactGeoLocationHistory.id DESC