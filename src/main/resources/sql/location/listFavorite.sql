SELECT
	locationFavorite.id,
	locationFavorite.favorite,
	locationFavorite.locationId,
	locationFavorite.contactId,
	locationFavorite.modifiedAt
FROM
	LocationFavorite locationFavorite
WHERE
	locationFavorite.favorite=true and
	{search}