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
	location.category,
	location.contactId,
	location.description,
	location.id,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	block.id,
	block.note,
	block.reason,
	'' as geolocationDistance
FROM
	Location location,
	Block block
WHERE
	block.locationId=location.id and
	block.contactId={USERID}