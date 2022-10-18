select
	contact.id
FROM
	Contact contact, GeoLocation geoLocation, ContactGeoLocationHistory contactGeoLocationHistory JOIN  ContactGeoLocationHistory h2
	on contactGeoLocationHistory.contactId=h2.contactId and
	contactGeoLocationHistory.id=(select max(id) from ContactGeoLocationHistory where contact_Id=contactGeoLocationHistory.contactId)
WHERE
	geoLocation.id=contactGeoLocationHistory.geoLocationId and
	contact.id=contactGeoLocationHistory.contactId and
	{search}
GROUP BY
	contact.id