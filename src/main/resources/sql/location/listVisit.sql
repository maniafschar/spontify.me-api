select
	location.address,
	location.category,
	location.contactId,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	locationFavorite.modifiedAt,
	max(locationVisit.createdAt) as maxDate,
	'' as geolocationDistance
FROM
	LocationVisit locationVisit, Location location left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID}
WHERE
	locationVisit.contactId={USERID} and
	locationVisit.locationId=location.id and
	{search}
GROUP BY
	location.id
ORDER BY
	maxDate DESC