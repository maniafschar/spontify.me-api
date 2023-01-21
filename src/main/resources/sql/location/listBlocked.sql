select
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