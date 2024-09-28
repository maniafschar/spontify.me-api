select
	location.address,
	location.contactId,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.rating,
	location.skills,
	location.skillsText,
	location.telephone,
	location.town,
	location.url,
	locationFavorite.id,
	locationFavorite.favorite,
	locationFavorite.modifiedAt,
	'' as geolocationDistance
FROM
	Location location left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID}
WHERE
	(location.skills is null or location.skills<>'X') and location.category like '%2%' and
	{search}
GROUP BY
	location.id,
	locationFavorite.id