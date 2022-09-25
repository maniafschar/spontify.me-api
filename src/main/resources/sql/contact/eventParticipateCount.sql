select
	count(*) as c
FROM
	EventParticipate eventParticipate
WHERE
	{search}