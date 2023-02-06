SELECT
	contact.id,
	contact.imageList,
	contact.pseudonym,
	contact.latitude,
	contact.longitude,
	'' as geolocationDistance
FROM
	Contact contact
WHERE
	contact.imageList is not null and
	{search}