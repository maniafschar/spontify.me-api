SELECT
	event.category,
	event.confirm,
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.marketingEvent,
	event.maxParticipants,
	event.price,
	event.startDate,
	event.text,
	event.type,
	event.visibility,
	contact.id,
	contact.imageList,
	contact.pseudonym,
	location.address,
	location.attr0,
	location.attr1,
	location.attr2,
	location.attr3,
	location.attr4,
	location.attr5,
	location.category,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.ownerId,
	location.parkingOption,
	location.rating,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance,
	sum(case when WEEKDAY(current_timestamp)=locationOpenTime.day and locationOpenTime.openAt<=current_time and (locationOpenTime.closeAt>current_time or locationOpenTime.closeAt='00:00:00') then 1 else 0 end) as isOpen,
	count(locationOpenTime.id) as openTimesEntries
FROM
	Event event left join
	Location location on event.locationId=location.id left join
	LocationOpenTime locationOpenTime on locationOpenTime.locationId=location.id left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact
WHERE
	event.contactId=contact.id and
	{search}
GROUP BY
	event.id