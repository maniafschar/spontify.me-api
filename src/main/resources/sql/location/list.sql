select
	location.attr0,
	location.attr0Ex,
	location.attr1,
	location.attr1Ex,
	location.attr2,
	location.attr2Ex,
	location.attr3,
	location.attr3Ex,
	location.attr4,
	location.attr4Ex,
	location.attr5,
	location.attr5Ex,
	location.address,
	location.budget,
	location.category,
	location.contactId,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.rating,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	locationFavorite.modifiedAt,
	'' as geolocationDistance
FROM
	Location location left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID}
WHERE
	{search}
GROUP BY
	location.id,
	locationFavorite.id