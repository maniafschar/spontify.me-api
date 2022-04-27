SELECT
	locationOpenTime.id,
	locationOpenTime.locationId,
	locationOpenTime.day,
	locationOpenTime.openAt,
	locationOpenTime.closeAt
FROM
	LocationOpenTime locationOpenTime
WHERE
	{search}
ORDER BY
	locationOpenTime.day,
	locationOpenTime.openAt