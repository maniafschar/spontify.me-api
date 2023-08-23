SELECT
	clientMarketing.id,
	clientMarketing.startDate,
	clientMarketing.endDate,
	clientMarketing.age,
	clientMarketing.gender,
	clientMarketing.region,
	clientMarketing.language,
	clientMarketing.storage
FROM
	ClientMarketing clientMarketing
WHERE
	{search}
ORDER BY
	clientMarketing.startDate DESC
