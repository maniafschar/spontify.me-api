SELECT
	ai.id,
	ai.question,
	ai.note,
	ai.type,
	ai.modifiedAt,
	ai.createdAt
FROM
	Ai ai
WHERE
	{search}