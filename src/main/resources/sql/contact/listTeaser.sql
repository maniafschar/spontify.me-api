SELECT
	contact.age,
	contact.description,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.id,
	contact.idDisplay,
	contact.image,
	contact.imageList,
	contact.latitude,
	contact.longitude,
	contact.pseudonym,
	contact.rating,
	contact.skills,
	contact.skillsText,
	'' as geolocationDistance
FROM
	Contact contact 
		left join ContactGeoLocationHistory contactGeoLocationHistory
			on contactGeoLocationHistory.contactId=contact.id
				and contactGeoLocationHistory.id=(select max(id) from ContactGeoLocationHistory where contactId=contact.id)
		left join GeoLocation geoLocation on contactGeoLocationHistory.geoLocationId=geoLocation.id
WHERE
	contact.imageList is not null and
	contact.clientId={CLIENTID} and
	contact.type<>'admin' and
	{search}