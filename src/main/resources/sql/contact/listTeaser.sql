SELECT
	contact.imageList,
	contact.pseudonym,
	contact.latitude,
	contact.longitude,
	'' as geolocationDistance
FROM
	Contact contact
WHERE
	contact.verified=1 and
	contact.imageList is not null and
	{search}