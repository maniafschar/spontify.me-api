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
	clientMarketing.clientId={CLIENTID} AND
	{search}
ORDER BY
	clientMarketing.startDate DESC
