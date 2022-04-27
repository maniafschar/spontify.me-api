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
	location.bonus,
	location.category,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.openTimesBankholiday,
	location.openTimesText,
	location.ownerId,
	location.parkingOption,
	location.parkingText,
	location.rating,
	location.town,
	sum(case when WEEKDAY(current_timestamp)+1=day and locationOpenTime.openAt<=current_time and (locationOpenTime.closeAt>current_time or locationOpenTime.closeAt<=locationOpenTime.openAt) then 1 else 0 end) as isOpen,
	count(locationOpenTime.id) as openTimesEntries,
	locationFavorite.id,
	locationFavorite.favorite,
	locationFavorite.modifiedAt,
	max(locationVisit.createdAt) as maxDate,
	'' as geolocationDistance
FROM
	LocationVisit locationVisit, Location location left join LocationOpenTime locationOpenTime on locationOpenTime.locationId=location.id left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID}
WHERE
	locationVisit.contactId={USERID} and
	locationVisit.locationId=location.id and
	{search}
GROUP BY
	location.id
ORDER BY
	maxDate DESC