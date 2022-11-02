select
	event.id,
	event.type,
	event.confirm,
	event.startDate,
	event.endDate,
	event.text,
	event.image,
	event.imageList,
	event.price,
	event.marketingEvent,
	event.maxParticipants,
	event.visibility,
	contact.id,
	contact.pseudonym,
	contact.imageList,
	location.id,
	location.ownerId,
	location.category,
	location.name,
	location.town,
	location.description,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.rating,
	location.parkingOption,
	location.address,
	location.attr0,
	location.attr1,
	location.attr2,
	location.attr3,
	location.attr4,
	location.attr5,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance,
	sum(case when WEEKDAY(current_timestamp)=locationOpenTime.day and locationOpenTime.openAt<=current_time and (locationOpenTime.closeAt>current_time or locationOpenTime.closeAt='00:00:00') then 1 else 0 end) as isOpen,
	count(locationOpenTime.id) as openTimesEntries
FROM
	Event event left join EventParticipate eventParticipate on eventParticipate.contactId={USERID} and eventParticipate.eventId=event.id,
	Location location left join LocationOpenTime locationOpenTime on locationOpenTime.locationId=location.id left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact
WHERE
	TO_DAYS(event.startDate)-14<=TO_DAYS(current_timestamp) and
	TO_DAYS(event.endDate)>=TO_DAYS(current_timestamp) and
	(
		event.visibility=3 or 
		event.contactId={USERID} or
		(
			select cl from ContactLink cl where cl.status=1 and (cl.contactId={USERID} and cl.contactId2=event.contactId or cl.contactId2={USERID} and cl.contactId=event.contactId)
		) is not null or
		event.visibility=2 and
		(REGEXP_LIKE(contact.attrInterest, '{USERATTRIBUTES}')=1 or REGEXP_LIKE(contact.attrInterestEx, '{USERATTRIBUTESEX}')=1) and
		(
			{USERGENDER}=1 and contact.ageMale like '%,%' and {USERAGE}>=substring(contact.ageMale,1,2) and {USERAGE}<=substring(contact.ageMale,4,2) or
			{USERGENDER}=2 and contact.ageFemale like '%,%' and {USERAGE}>=substring(contact.ageFemale,1,2) and {USERAGE}<=substring(contact.ageFemale,4,2) or
			{USERGENDER}=3 and contact.ageDivers like '%,%' and {USERAGE}>=substring(contact.ageDivers,1,2) and {USERAGE}<=substring(contact.ageDivers,4,2)
		)
	) and
	event.locationId=location.id and
	event.contactId=contact.id and
	{search}
GROUP BY
	location.id,
	event.id,
	contact.id,
	locationFavorite.id
